package org.folio.circulation.domain;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.Item;
import org.folio.circulation.support.ValidationErrorFailure;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE_PROPERTY_NAME;
import static org.folio.circulation.support.HttpResult.failure;

public class AlreadyCheckedOutValidator {
  public HttpResult<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(loan -> {
      final Item records = loan.getLoan().getItem();

      if(records.isCheckedOut()) {
        return failure(ValidationErrorFailure.failure(
          "Item is already checked out", "itemId", records.getItemId()));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }

  public HttpResult<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords, String barcode) {

    //TODO: Extract duplication with above
    return loanAndRelatedRecords.next(loan -> {
      if(loan.getLoan().getItem().isCheckedOut()) {
        return failure(ValidationErrorFailure.failure(
          "Item is already checked out", ITEM_BARCODE_PROPERTY_NAME, barcode));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }
}
