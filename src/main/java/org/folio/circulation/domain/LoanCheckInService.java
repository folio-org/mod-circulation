package org.folio.circulation.domain;

import static org.folio.circulation.support.Result.of;

import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.Result;

public class LoanCheckInService {
  public Result<Loan> checkIn(Loan loan, CheckInByBarcodeRequest request) {
    return of(() -> loan == null
        ? null
        : loan.checkIn(request.getCheckInDate(), request.getServicePointId()));
  }
}
