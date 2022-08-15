package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.ItemStatusName.CLAIMED_RETURNED;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.succeeded;

import org.folio.circulation.domain.ItemStatusName;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.results.Result;

public final class NotInItemStatusValidator {

  private NotInItemStatusValidator() {}

  public static Result<Loan> refuseWhenItemIsNotClaimedReturned(
    Result<Loan> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .next(p -> refuseWhenItemIsNotInStatus(loanAndRelatedRecords, CLAIMED_RETURNED));
  }

  private static Result<Loan> refuseWhenItemIsNotInStatus(
    Result<Loan> loanResult, ItemStatusName status) {

    return loanResult.failWhen(
      records -> succeeded(loanResult.value().getItem().isNotInStatus(status)),
      loan -> singleValidationError(String.format("Item is not %s",
        status.getName()), "itemId", loanResult.value().getItem().getItemId()));
  }
}
