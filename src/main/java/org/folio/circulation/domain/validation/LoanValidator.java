package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;

public class LoanValidator {

  private LoanValidator() {}

  public static Result<Loan> refuseWhenLoanIsClosed(Result<Loan> loanResult) {
    return loanResult.failWhen(
      loan -> succeeded(loan.isClosed()),
      loan -> singleValidationError("Loan is closed", "id", loan.getId())
    );
  }

  public static Result<Loan> refuseWhenItemIsDeclaredLost(
    Result<Loan> loanResult) {

    return loanResult.failWhen(
      loan -> succeeded(loan.getItem().isInStatus(DECLARED_LOST)),
      loan -> singleValidationError(new ValidationError("item is Declared lost",
         ITEM_ID, loan.getItem().getItemId())));
  }

  public static Result<Loan> refuseWhenItemIsClaimedReturned(
    Result<Loan> loanResult) {

    return loanResult.failWhen(
      loan -> succeeded(loan.getItem().isInStatus(CLAIMED_RETURNED)),
      loan -> singleValidationError(new ValidationError("item is Claimed returned",
         ITEM_ID, loan.getItem().getItemId())));
  }
}
