package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.results.Result;

public final class NotInItemStatusValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private NotInItemStatusValidator() {}

  public static Result<Loan> refuseWhenItemIsNotClaimedReturned(
    Result<Loan> loanAndRelatedRecords) {

    log.debug("refuseWhenItemIsNotClaimedReturned:: parameters loanAndRelatedRecords={}",
      () -> resultAsString(loanAndRelatedRecords));

    return loanAndRelatedRecords
      .next(p -> refuseWhenItemIsNotInStatus(loanAndRelatedRecords, CLAIMED_RETURNED));
  }

  private static Result<Loan> refuseWhenItemIsNotInStatus(
    Result<Loan> loanResult, ItemStatus status) {

    log.debug("refuseWhenItemIsNotInStatus:: parameters loanResult={}, status={}",
      () -> resultAsString(loanResult), () -> status);

    return loanResult.failWhen(
      records -> succeeded(loanResult.value().getItem().isNotInStatus(status)),
      loan -> singleValidationError(String.format("Item is not %s",
        status.getValue()), "itemId", loanResult.value().getItem().getItemId()));
  }
}
