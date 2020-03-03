package org.folio.circulation.domain;

import static org.folio.circulation.domain.LoanAction.ResolveClaimedReturned;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;

import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;

public class LoanCheckInService {

  public Result<Loan> checkIn(Loan loan, DateTime systemDateTime,
    CheckInByBarcodeRequest request) {

    if (loan == null) {
      return of(() -> null);
    }

    if (loan.getItem().isClaimedReturned()) {
      return succeeded(loan
        .resolveClaimedReturned(getClaimedReturnedResolveAction(request),
          request.getCheckInDate(), systemDateTime, request.getServicePointId()));
    }

    return succeeded(loan.checkIn(request.getCheckInDate(), systemDateTime,
      request.getServicePointId()));
  }

  public boolean isInHouseUse(Item item, RequestQueue requestQueue,
    CheckInByBarcodeRequest checkInByBarcodeRequest) {

    return item.isAvailable()
      && requestQueue.getRequests().isEmpty()
      && item.getLocation().homeLocationIsServedBy(checkInByBarcodeRequest
      .getServicePointId());
  }

  private ResolveClaimedReturned getClaimedReturnedResolveAction(
    CheckInByBarcodeRequest request) {

    switch (request.getClaimedReturnedResolution()) {
      case FOUND_BY_LIBRARY:
        return ResolveClaimedReturned.CHECKED_IN_FOUND_BY_LIBRARY;
      case RETURNED_BY_PATRON:
        return ResolveClaimedReturned.CHECKED_IN_RETURNED_BY_PATRON;
      default:
        throw new IllegalArgumentException(
          "No loan action to resolve claimed returned: "
            + request.getClaimedReturnedResolution());
    }
  }
}
