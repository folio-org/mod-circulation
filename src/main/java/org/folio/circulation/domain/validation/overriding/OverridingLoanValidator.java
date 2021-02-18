package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.domain.LoanAction.CHECKED_OUT_THROUGH_OVERRIDE;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.override.ItemNotLoanableBlockOverride;
import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.results.Result;

public class OverridingLoanValidator extends OverridingBlockValidator<LoanAndRelatedRecords> {
  public OverridingLoanValidator(OverridableBlockType blockType, BlockOverrides blockOverrides,
    OkapiPermissions permissions) {

    super(blockType, blockOverrides, permissions);
  }

  @Override
  public CompletableFuture<Result<LoanAndRelatedRecords>> validate(LoanAndRelatedRecords records) {
    return super.validate(records)
      .thenApply(result -> result.map(this::setDueDateIfNotLoanableOverriding)
      .map(this::setLoanAction));
  }

  private LoanAndRelatedRecords setLoanAction(LoanAndRelatedRecords loanAndRelatedRecords) {
    Loan loan = loanAndRelatedRecords.getLoan();
    loan.changeAction(CHECKED_OUT_THROUGH_OVERRIDE);
    loan.changeActionComment(getBlockOverrides().getComment());

    return loanAndRelatedRecords;
  }

  private LoanAndRelatedRecords setDueDateIfNotLoanableOverriding(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    ItemNotLoanableBlockOverride itemNotLoanableBlockOverride = getBlockOverrides()
      .getItemNotLoanableBlockOverride();

    if (itemNotLoanableBlockOverride.isRequested()) {
      loanAndRelatedRecords.getLoan().changeDueDate(itemNotLoanableBlockOverride.getDueDate());
    }

    return loanAndRelatedRecords;
  }
}
