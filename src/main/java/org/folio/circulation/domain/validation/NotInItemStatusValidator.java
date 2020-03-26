package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Result;

public final class NotInItemStatusValidator {

  private NotInItemStatusValidator(){ }

  public static Result<Loan> refuseWhenItemIsNotClaimedReturned(
    Result<Loan> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .next(p -> refuseWhenItemIsNotInStatus(loanAndRelatedRecords, CLAIMED_RETURNED));
  }

  private static Result<Loan> refuseWhenItemIsNotInStatus(
    Result<Loan> loanResult, ItemStatus status) {

    return loanResult.failWhen(
      records -> succeeded(!loanResult.value().getItem().isInStatus(status)),
      loan -> singleValidationError(String.format("Item is not %s",
        status.getValue()), "id", loanResult.value().getItem().getItemId()));
  }
}
