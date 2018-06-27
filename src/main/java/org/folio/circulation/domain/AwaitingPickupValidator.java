package org.folio.circulation.domain;

import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import static org.folio.circulation.support.HttpResult.failure;

public class AwaitingPickupValidator {
  public HttpResult<LoanAndRelatedRecords> refuseWhenUserIsNotAwaitingPickup(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .next(loan -> {
      final RequestQueue requestQueue = loan.getRequestQueue();
      final User requestingUser = loan.getLoan().getUser();
      String itemTitle = loan.getLoan().getItem().getTitle();
      String itemBarcode = loan.getLoan().getItem().getBarcode();

        if(requestQueue.hasAwaitingPickupRequestForOtherPatron(requestingUser)) {
        return failure(ValidationErrorFailure.failure(
          String.format("%s (Barcode: %s) cannot be checked out to user %s because it is awaiting pickup by another patron",
          itemTitle, itemBarcode, requestingUser.getPersonalName()),
          "userId", loan.getLoan().getUserId()));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }

  public HttpResult<LoanAndRelatedRecords> refuseWhenUserIsNotAwaitingPickup(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords, String barcode) {

    //TODO: Extract duplication between this and above
    return loanAndRelatedRecords.next(loan -> {
      final RequestQueue requestQueue = loan.getRequestQueue();
      final User requestingUser = loan.getLoan().getUser();
      String itemTitle = loan.getLoan().getItem().getTitle();
      String itemBarcode = loan.getLoan().getItem().getBarcode();

      if(requestQueue.hasAwaitingPickupRequestForOtherPatron(requestingUser)) {
        return failure(ValidationErrorFailure.failure(
          String.format("%s (Barcode: %s) cannot be checked out to user %s because it is awaiting pickup by another patron",
          itemTitle, itemBarcode, requestingUser.getPersonalName()),
          CheckOutByBarcodeRequest.USER_BARCODE_PROPERTY_NAME, barcode));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }
}
