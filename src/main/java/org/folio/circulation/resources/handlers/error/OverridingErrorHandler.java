package org.folio.circulation.resources.handlers.error;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INSUFFICIENT_OVERRIDE_PERMISSIONS;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.resources.handlers.error.override.OverridableBlockType.PATRON_BLOCK;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.folio.circulation.resources.handlers.error.override.BlockOverride;
import org.folio.circulation.resources.handlers.error.override.BlockOverrides;
import org.folio.circulation.resources.handlers.error.override.InsufficientOverridePermissionsError;
import org.folio.circulation.resources.handlers.error.override.OverridableBlockType;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.OverridableValidationError;
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

  private final List<String> okapiPermissions;
  private final BlockOverrides blockOverrides;

  @Override
  public <T> Result<T> handleValidationError(HttpFailure error, CirculationErrorType errorType,
    Result<T> otherwise) {

    final OverridableBlockType blockType = OVERRIDABLE_ERROR_TYPES.get(errorType);

    return blockOverrides.getOverrideOfType(blockType)
      .map(override -> handleOverride(override, error, errorType, blockType, otherwise))
      .orElseGet(() -> super.handleValidationError(error, errorType, otherwise));
  }

  private <T> Result<T> handleOverride(BlockOverride override, HttpFailure error,
    CirculationErrorType errorType, OverridableBlockType blockType, Result<T> otherwise) {

    final List<String> missingPermissions = override.getBlockType()
      .getMissingPermissions(okapiPermissions);

    if (!missingPermissions.isEmpty()) {
      log.warn("Unable to override {} caused by error {}. Missing permissions: {}",
        blockType, errorType, missingPermissions);

      return super.handleValidationError(error, errorType, otherwise)
        .next(r -> handleMissingPermissions(blockType, missingPermissions, otherwise));
    }

    log.info("Overriding {} caused by error {}", blockType, errorType);

    return otherwise;
  }

  private <T> Result<T> handleMissingPermissions(OverridableBlockType blockType,
    List<String> missingPermissions, Result<T> otherwise) {

    return super.handleValidationError(
      singleValidationError(new InsufficientOverridePermissionsError(blockType, missingPermissions)),
      INSUFFICIENT_OVERRIDE_PERMISSIONS, otherwise);
  }

  @Override
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
      List<String> missingPermissions = blockType.getMissingPermissions(okapiPermissions);

      List<ValidationError> overridableValidationErrors = validationFailure.getErrors().stream()
        .map(error -> new OverridableValidationError(error, blockType, missingPermissions))
        .collect(toList());

      return new ValidationErrorFailure(overridableValidationErrors);
    }

    return validationFailure;
  }
}
