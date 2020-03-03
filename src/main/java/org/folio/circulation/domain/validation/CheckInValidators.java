package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.representations.CheckInByBarcodeRequest.CLAIMED_RETURNED_RESOLUTION;
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
      processRecords -> singleValidationError(
        "Item is claimed returned, a resolution is required to check in",
        CLAIMED_RETURNED_RESOLUTION, null));
  }

  private static boolean isClaimedReturnedNotResolved(CheckInProcessRecords processRecords) {
    return processRecords.getItem().isClaimedReturned()
      && processRecords.getCheckInRequest().getClaimedReturnedResolution() == null;
  }
}
