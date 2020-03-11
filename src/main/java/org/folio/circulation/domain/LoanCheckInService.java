package org.folio.circulation.domain;

import static org.folio.circulation.domain.LoanAction.ResolveClaimedReturned;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;

import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.DateTime;

public class LoanCheckInService {

  public Result<Loan> checkIn(Loan loan, DateTime systemDateTime,
    CheckInByBarcodeRequest request) {

    if (loan == null) {
      return of(() -> null);
    }

    if (loan.getItem().isClaimedReturned()) {
      return getClaimedReturnedResolveAction(request)
        .map(action -> loan.resolveClaimedReturned(action, request.getCheckInDate(),
          systemDateTime, request.getServicePointId()));
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

  private Result<ResolveClaimedReturned> getClaimedReturnedResolveAction(
    CheckInByBarcodeRequest request) {

    switch (request.getClaimedReturnedResolution()) {
      case FOUND_BY_LIBRARY:
        return succeeded(ResolveClaimedReturned.CHECKED_IN_FOUND_BY_LIBRARY);
      case RETURNED_BY_PATRON:
        return succeeded(ResolveClaimedReturned.CHECKED_IN_RETURNED_BY_PATRON);
      default:
        return failed(new ServerErrorFailure("No loan action to resolve claimed returned: "
          + request.getClaimedReturnedResolution()));
    }
  }
}
