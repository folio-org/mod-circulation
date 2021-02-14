package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.domain.LoanAction.CHECKED_OUT_THROUGH_OVERRIDE;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.representations.OverrideBlocks;
import org.folio.circulation.resources.handlers.error.OverridableBlockType;
import org.folio.circulation.support.results.Result;

public abstract class LoanOverrideValidator extends OverrideValidator<LoanAndRelatedRecords> {
  public LoanOverrideValidator(OverridableBlockType blockType, OverrideBlocks overrideBlocks,
    List<String> permissions) {

    super(blockType, overrideBlocks, permissions);
  }

  @Override
  public CompletableFuture<Result<LoanAndRelatedRecords>> validate(LoanAndRelatedRecords records) {
    return super.validate(records)
      .thenApply(r -> r.map(this::setLoanAction));
  }

  private LoanAndRelatedRecords setLoanAction(LoanAndRelatedRecords loanAndRelatedRecords) {

    Loan loan = loanAndRelatedRecords.getLoan();
    loan.changeAction(CHECKED_OUT_THROUGH_OVERRIDE);
    loan.changeActionComment(getOverrideBlocks().getComment());

    return loanAndRelatedRecords;
  }
}
