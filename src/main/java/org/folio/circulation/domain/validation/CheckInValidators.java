package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.representations.CheckInByBarcodeRequest.CLAIMED_RETURNED_RESOLVED_BY;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.support.Result;

public final class CheckInValidators {

  private CheckInValidators() {
  }

  public static Result<CheckInProcessRecords> refuseWhenClaimedReturnedIsNotResolved(
    Result<CheckInProcessRecords> processRecordsResult) {

    return processRecordsResult.failWhen(
      processRecords -> succeeded(isClaimedReturnedNotResolved(processRecords)),
      processRecords -> singleValidationError(String.format(
        "Item has Claimed returned status but no '%s' present", CLAIMED_RETURNED_RESOLVED_BY),
        CLAIMED_RETURNED_RESOLVED_BY, null));
  }

  private static boolean isClaimedReturnedNotResolved(CheckInProcessRecords processRecords) {
    return processRecords.getItem().isClaimedReturned()
      && processRecords.getCheckInRequest().getClaimedReturnedResolvedBy() == null;
  }
}
