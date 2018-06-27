package org.folio.circulation.domain;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE_PROPERTY_NAME;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.PROXY_USER_BARCODE_PROPERTY_NAME;
import static org.folio.circulation.support.HttpResult.failure;
import static org.folio.circulation.support.HttpResult.success;

public class LoanValidation {
  private LoanValidation() { }

  public static CompletableFuture<HttpResult<LoanAndRelatedRecords>> refuseWhenHasOpenLoan(
    LoanAndRelatedRecords loanAndRelatedRecords,
    LoanRepository loanRepository,
    String barcode) {

    final String itemId = loanAndRelatedRecords.getLoan().getItemId();

    return loanRepository.hasOpenLoan(itemId)
      .thenApply(r -> r.next(openLoan -> {
        if(openLoan) {
          return failure(ValidationErrorFailure.failure(
            "Cannot check out item that already has an open loan",
            ITEM_BARCODE_PROPERTY_NAME, barcode));
        }
        else {
          return success(loanAndRelatedRecords);
        }
      }));
  }
}
