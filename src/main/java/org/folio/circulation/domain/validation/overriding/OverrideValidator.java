package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INSUFFICIENT_OVERRIDE_PERMISSIONS;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.representations.OverrideBlocks;
import org.folio.circulation.resources.handlers.error.CirculationErrorType;
import org.folio.circulation.resources.handlers.error.OverridableBlockType;
import org.folio.circulation.support.http.server.InsufficientOverridePermissionsError;
import org.folio.circulation.support.results.Result;

import lombok.Getter;

@Getter
public abstract class OverrideValidator<T> implements Validator<T> {
  private final OverridableBlockType blockType;
  private final OverrideBlocks overrideBlocks;
  private final List<String> permissions;

  public OverrideValidator(OverridableBlockType blockType, OverrideBlocks overrideBlocks,
    List<String> permissions) {

    this.blockType = blockType;
    this.permissions = permissions;
    this.overrideBlocks = overrideBlocks;
  }

  @Override
  public CompletableFuture<Result<T>> validate(T records) {
    List<String> missingPermissions = blockType.getMissingOverridePermissions(permissions);

    return succeeded(records)
      .failAfter(r -> ofAsync(() -> !missingPermissions.isEmpty()),
        r -> singleValidationError(
          new InsufficientOverridePermissionsError(blockType, missingPermissions)));
  }

  @Override
  public CirculationErrorType getErrorType() {
    return INSUFFICIENT_OVERRIDE_PERMISSIONS;
  }
}
