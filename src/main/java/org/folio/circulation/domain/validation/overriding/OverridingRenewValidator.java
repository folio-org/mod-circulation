package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.domain.LoanAction.RENEWED_THROUGH_OVERRIDE;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
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
      .thenApply(result -> result.map(this::setLoanAction));
  }

  private RenewalContext setLoanAction(RenewalContext context) {
    Loan loan = context.getLoan();
    loan.changeAction(RENEWED_THROUGH_OVERRIDE);
    loan.changeActionComment(getBlockOverrides().getComment());

    return context;
  }
}
