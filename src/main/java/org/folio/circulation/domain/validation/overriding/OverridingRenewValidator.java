package org.folio.circulation.domain.validation.overriding;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.results.Result;

public class OverridingRenewValidator extends OverridingBlockValidator<RenewalContext> {
  public OverridingRenewValidator(OverridableBlockType blockType, BlockOverrides blockOverrides,
    OkapiPermissions permissions) {

    super(blockType, blockOverrides, permissions);
  }

  @Override
  public CompletableFuture<Result<RenewalContext>> validate(RenewalContext context) {
    return super.validate(context)
      .thenApply(result -> result.map(ctx -> ctx.withPatronBlockOverridden(true)));
  }
}
