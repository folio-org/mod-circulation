package org.folio.circulation.resources;

import static org.folio.circulation.domain.override.BlockOverrides.noOverrides;
import static org.folio.circulation.domain.override.OverridableBlockType.PATRON_BLOCK;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;

import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.validation.AutomatedPatronBlocksValidator;
import org.folio.circulation.domain.validation.UserManualBlocksValidator;
import org.folio.circulation.domain.validation.overriding.BlockValidator;
import org.folio.circulation.domain.validation.overriding.OverridingBlockValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.OkapiPermissions;

import lombok.Getter;

@Getter
public class RequestBlockValidators {
  private final BlockValidator<RequestAndRelatedRecords> manualPatronBlocksValidator;
  private final BlockValidator<RequestAndRelatedRecords> automatedPatronBlocksValidator;

  public RequestBlockValidators(BlockOverrides blockOverrides,
    OkapiPermissions permissions, Clients clients) {

    manualPatronBlocksValidator = blockOverrides.getPatronBlockOverride().isRequested()
      ? new OverridingBlockValidator<>(PATRON_BLOCK, blockOverrides, permissions)
      : new BlockValidator<>(USER_IS_BLOCKED_MANUALLY,
      new UserManualBlocksValidator(clients)::refuseWhenUserIsBlocked);

    automatedPatronBlocksValidator = blockOverrides.getPatronBlockOverride().isRequested()
      ? new OverridingBlockValidator<>(PATRON_BLOCK, blockOverrides, permissions)
      : new BlockValidator<>(USER_IS_BLOCKED_AUTOMATICALLY,
      new AutomatedPatronBlocksValidator(clients)::refuseWhenRequestActionIsBlockedForPatron);
  }

  public static RequestBlockValidators regularRequestBlockValidators(Clients clients) {
    return new RequestBlockValidators(noOverrides(), OkapiPermissions.empty(), clients);
  }
}