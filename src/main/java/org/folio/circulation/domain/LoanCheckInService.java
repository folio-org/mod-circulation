package org.folio.circulation.domain;

import static org.folio.circulation.support.Result.of;

import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.Result;

import org.joda.time.DateTime;

public class LoanCheckInService {

  public Result<Loan> checkIn(Loan loan, DateTime systemDateTime,
    CheckInByBarcodeRequest request) {
    return of(() -> loan == null
      ? null
      : loan.checkIn(request.getCheckInDate(), systemDateTime,
        request.getServicePointId()));
  }

  public boolean isInHouseUse(Item item, RequestQueue requestQueue,
    CheckInByBarcodeRequest checkInByBarcodeRequest) {

    return item.isAvailable()
      && requestQueue.getRequests().isEmpty()
      && item.getLocation().homeLocationIsServedBy(checkInByBarcodeRequest
      .getServicePointId());
  }
}
