package org.folio.circulation.domain;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE_PROPERTY_NAME;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.HttpResult.failure;

public class ItemNotFoundValidator {

  public HttpResult<LoanAndRelatedRecords> refuseWhenItemNotFound(
    HttpResult<LoanAndRelatedRecords> result, String barcode) {

    return result.next(loanAndRelatedRecords -> {
      if(loanAndRelatedRecords.getLoan().getItem().isNotFound()) {
        return failure(ValidationErrorFailure.failure(
          String.format("No item with barcode %s exists", barcode),
          ITEM_BARCODE_PROPERTY_NAME, barcode));
      }
      else {
        return result;
      }
    });
  }

  public HttpResult<LoanAndRelatedRecords> refuseWhenItemNotFound(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      if(loan.getLoan().getItem().isNotFound()) {
        return failure(ValidationErrorFailure.failure(
          "Item does not exist", ITEM_ID, loan.getLoan().getItemId()));
      }
      else {
        return result;
      }
    });
  }
}
