package org.folio.circulation.domain.validation.overriding;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INSUFFICIENT_OVERRIDE_PERMISSIONS;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.support.http.server.InsufficientOverridePermissionsError;
import org.folio.circulation.support.http.OkapiPermissions;

import lombok.Getter;

@Getter
public class OverridingBlockValidator<T> extends BlockValidator<T> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final OverridableBlockType blockType;
  private final BlockOverrides blockOverrides;
  private final OkapiPermissions permissions;

  public OverridingBlockValidator(OverridableBlockType blockType, BlockOverrides blockOverrides,
    OkapiPermissions permissions) {

    super(INSUFFICIENT_OVERRIDE_PERMISSIONS, otherwise -> {
      log.info("OverridingBlockValidator validationFunction:: blockType: {}, " +
        "blockOverrides: {}, permissions: {}", blockType, blockOverrides, permissions);
      OkapiPermissions missingPermissions = blockType.getMissingOverridePermissions(permissions);
      log.info("OverridingBlockValidator validationFunction:: missingPermissions: {}",
        missingPermissions);
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