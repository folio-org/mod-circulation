package org.folio.circulation.domain;

import static org.folio.circulation.domain.LoanAction.RESOLVE_CLAIM_AS_FOUND_BY_LIBRARY;
import static org.folio.circulation.domain.LoanAction.RESOLVE_CLAIM_AS_RETURNED_BY_PATRON;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;

public class LoanCheckInService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public Result<Loan> checkIn(Loan loan, ZonedDateTime systemDateTime,
    CheckInByBarcodeRequest request) {

    log.debug("checkIn:: parameters loan: {}, systemDateTime: {}, request: {}",
      () -> loan, () -> systemDateTime, () -> request);
    if (loan == null) {
      log.debug("checkIn:: loan is null");
      return of(() -> null);
    }

    if (loan.getItem().isClaimedReturned()) {
      return getClaimedReturnedResolveAction(request)
        .map(action -> loan.resolveClaimedReturned(action, request.getCheckInDate(),
          systemDateTime, request.getServicePointId()));
    }

    if (loan.isAgedToLost()) {
      log.info("checkIn:: loan is agedToLost, removing aged to lost billing info");
      loan.removeAgedToLostBillingInfo();
    }

    if (loan.isForUseAtLocation()) {
      loan.changeStatusOfUsageAtLocation("Returned", null);
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

  private Result<LoanAction> getClaimedReturnedResolveAction(
    CheckInByBarcodeRequest request) {

    switch (request.getClaimedReturnedResolution()) {
      case FOUND_BY_LIBRARY:
        return succeeded(RESOLVE_CLAIM_AS_FOUND_BY_LIBRARY);
      case RETURNED_BY_PATRON:
        return succeeded(RESOLVE_CLAIM_AS_RETURNED_BY_PATRON);
      default:
        return failed(new ServerErrorFailure("No loan action to resolve claimed returned: "
          + request.getClaimedReturnedResolution()));
    }
  }
}
