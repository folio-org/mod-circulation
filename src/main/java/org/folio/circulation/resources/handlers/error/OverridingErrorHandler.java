package org.folio.circulation.resources.handlers.error;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.override.OverridableBlockType.PATRON_BLOCK;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.BlockValidationError;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class OverridingErrorHandler extends DeferFailureErrorHandler {
  private static final Map<CirculationErrorType, OverridableBlockType> OVERRIDABLE_ERROR_TYPES =
    Map.ofEntries(
      entry(USER_IS_BLOCKED_MANUALLY, PATRON_BLOCK),
      entry(USER_IS_BLOCKED_AUTOMATICALLY, PATRON_BLOCK)
    );

  private final OkapiPermissions okapiPermissions;

  @Override
  public <T> Result<T> failWithValidationErrors(T otherwise) {
    List<ValidationError> validationErrors = getErrors().keySet().stream()
      .filter(ValidationErrorFailure.class::isInstance)
      .map(ValidationErrorFailure.class::cast)
      .map(this::extendOverridableErrors)
      .map(ValidationErrorFailure::getErrors)
      .flatMap(Collection::stream)
      .distinct() // to filter out duplicate InsufficientOverridePermission errors
      .collect(toList());

    return validationErrors.isEmpty()
      ? succeeded(otherwise)
      : failed(new ValidationErrorFailure(validationErrors));
  }

  private ValidationErrorFailure extendOverridableErrors(ValidationErrorFailure validationFailure) {
    final CirculationErrorType errorType = getErrors().get(validationFailure);

    if (OVERRIDABLE_ERROR_TYPES.containsKey(errorType)) {
      OverridableBlockType blockType = OVERRIDABLE_ERROR_TYPES.get(errorType);
      OkapiPermissions missingOverridePermissions =
        blockType.getMissingOverridePermissions(okapiPermissions);

      return new ValidationErrorFailure(
        validationFailure.getErrors().stream()
          .map(error -> new BlockValidationError(error, blockType, missingOverridePermissions))
          .collect(toList())
      );
    }

    return validationFailure;
  }
}
