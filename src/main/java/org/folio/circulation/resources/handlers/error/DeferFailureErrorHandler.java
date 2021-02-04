package org.folio.circulation.resources.handlers.error;

import static java.util.stream.Collectors.toList;
import static org.folio.circulation.resources.handlers.error.OverridableBlockType.PATRON_BLOCK;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.OkapiHeadersUtils.getOkapiPermissions;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.OverridableValidationError;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeferFailureErrorHandler extends CirculationErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final Map<CirculationErrorType, OverridableBlockType> OVERRIDABLE_ERROR_TYPES =
    Map.of(USER_IS_BLOCKED_MANUALLY, PATRON_BLOCK);

  private final Collection<String> okapiPermissions;

  public DeferFailureErrorHandler(Map<String, String> okapiHeaders) {
    super(new LinkedHashMap<>());
    this.okapiPermissions = getOkapiPermissions(okapiHeaders);
  }

  @Override
  public <T> Result<T> handleAnyResult(Result<T> result, CirculationErrorType errorType,
    Result<T> otherwise) {

    return result.mapFailure(error -> handleAnyError(error, errorType, otherwise));
  }

  @Override
  public <T> Result<T> handleAnyError(HttpFailure error, CirculationErrorType errorType,
    Result<T> otherwise) {

    if (error != null) {
      getErrors().put(error, errorType);
    } else {
      log.warn("HttpFailure object for error of type {} is null, error is ignored", errorType);
    }

    return otherwise;
  }

  @Override
  public <T> Result<T> handleValidationResult(Result<T> result, CirculationErrorType errorType,
    Result<T> otherwise) {

    return result.mapFailure(error -> handleValidationError(error, errorType, otherwise));
  }

  @Override
  public <T> Result<T> handleValidationError(HttpFailure error, CirculationErrorType errorType,
    Result<T> otherwise) {

    return error instanceof ValidationErrorFailure
      ? handleAnyError(error, errorType, otherwise)
      : failed(error);
  }

  public <T> Result<T> failWithValidationErrors(T otherwise) {
    List<ValidationError> validationErrors = getErrors().keySet().stream()
      .filter(ValidationErrorFailure.class::isInstance)
      .map(ValidationErrorFailure.class::cast)
      .map(this::enrichOverridableErrors)
      .map(ValidationErrorFailure::getErrors)
      .flatMap(Collection::stream)
      .collect(toList());

    return validationErrors.isEmpty()
      ? succeeded(otherwise)
      : failed(new ValidationErrorFailure(validationErrors));
  }

  private ValidationErrorFailure enrichOverridableErrors(ValidationErrorFailure validationFailure) {
    final CirculationErrorType errorType = getErrors().get(validationFailure);

    if (OVERRIDABLE_ERROR_TYPES.containsKey(errorType)) {
      OverridableBlockType blockType = OVERRIDABLE_ERROR_TYPES.get(errorType);
      List<String> missingPermissions = blockType.getMissingOverridePermissions(okapiPermissions);

      List<ValidationError> overridableValidationErrors = validationFailure.getErrors().stream()
        .map(error -> new OverridableValidationError(error, blockType, missingPermissions))
        .collect(toList());

      return new ValidationErrorFailure(overridableValidationErrors);
    }

    return validationFailure;
  }
}