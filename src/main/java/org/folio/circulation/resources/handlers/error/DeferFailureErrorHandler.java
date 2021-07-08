package org.folio.circulation.resources.handlers.error;

import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DeferFailureErrorHandler extends CirculationErrorHandler {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public DeferFailureErrorHandler() {
    super(new LinkedHashMap<>());
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

  @Override
  public <T> Result<T> failWithValidationErrors(T otherwise) {
    List<ValidationError> validationErrors = getErrors().keySet().stream()
      .filter(ValidationErrorFailure.class::isInstance)
      .map(ValidationErrorFailure.class::cast)
      .map(ValidationErrorFailure::getErrors)
      .flatMap(Collection::stream)
      .collect(toList());

    return validationErrors.isEmpty()
      ? succeeded(otherwise)
      : failed(new ValidationErrorFailure(validationErrors));
  }

}
