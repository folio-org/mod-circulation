package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.results.Result;

public class OverridingRenewValidator extends OverridingBlockValidator<RenewalContext> {
  private final Function<RenewalContext, CompletableFuture<Result<RenewalContext>>> validationFunction;

  public OverridingRenewValidator(OverridableBlockType blockType, BlockOverrides blockOverrides,
    OkapiPermissions permissions,
    Function<RenewalContext, CompletableFuture<Result<RenewalContext>>> validationFunction) {

    super(blockType, blockOverrides, permissions);
    this.validationFunction = validationFunction;
  }

  @Override
  public CompletableFuture<Result<RenewalContext>> validate(RenewalContext context) {
    return validationFunction.apply(context)
      .thenCompose(renewalContextResult -> {
        if (renewalContextResult.failed()) {
          return super.validate(context)
            .thenApply(result -> result.map(ctx -> ctx.withPatronBlockOverridden(true)));
        }
        return ofAsync(() -> context);
      });
  }
}
