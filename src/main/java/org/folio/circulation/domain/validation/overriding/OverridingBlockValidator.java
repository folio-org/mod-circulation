package org.folio.circulation.domain.validation.overriding;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INSUFFICIENT_OVERRIDE_PERMISSIONS;
import static org.folio.circulation.support.failures.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;

import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.support.http.server.error.InsufficientOverridePermissionsError;
import org.folio.circulation.support.http.OkapiPermissions;

import lombok.Getter;

@Getter
public class OverridingBlockValidator<T> extends BlockValidator<T> {
  private final OverridableBlockType blockType;
  private final BlockOverrides blockOverrides;
  private final OkapiPermissions permissions;

  public OverridingBlockValidator(OverridableBlockType blockType, BlockOverrides blockOverrides,
    OkapiPermissions permissions) {

    super(INSUFFICIENT_OVERRIDE_PERMISSIONS, otherwise -> {
      OkapiPermissions missingPermissions = blockType.getMissingOverridePermissions(permissions);
      return missingPermissions.isEmpty()
        ? ofAsync(() -> otherwise)
        : completedFuture(failed(singleValidationError(
          new InsufficientOverridePermissionsError(blockType, missingPermissions))));
    });

    this.blockType = blockType;
    this.permissions = permissions;
    this.blockOverrides = blockOverrides;
  }
}