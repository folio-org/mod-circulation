package org.folio.circulation.domain.validation.overriding;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INSUFFICIENT_OVERRIDE_PERMISSIONS;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.List;

import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.validation.Validator;
import org.folio.circulation.resources.handlers.error.OverridableBlockType;
import org.folio.circulation.support.http.server.InsufficientOverridePermissionsError;

import lombok.Getter;

@Getter
public class OverridingValidator<T> extends Validator<T> {
  private final OverridableBlockType blockType;
  private final BlockOverrides overrideBlocks;
  private final List<String> permissions;

  public OverridingValidator(OverridableBlockType blockType,
    BlockOverrides blockOverrides, List<String> permissions) {

    super(INSUFFICIENT_OVERRIDE_PERMISSIONS, otherwise -> {
      List<String> missingPermissions = blockType.getMissingOverridePermissions(permissions);

      return missingPermissions.isEmpty()
        ? ofAsync(() -> otherwise)
        : completedFuture(failed(singleValidationError(
        new InsufficientOverridePermissionsError(blockType, missingPermissions))));
    });

    this.blockType = blockType;
    this.permissions = permissions;
    this.overrideBlocks = blockOverrides;
  }
}
