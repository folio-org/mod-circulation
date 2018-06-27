package org.folio.circulation.domain;

import org.folio.circulation.support.HttpResult;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

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
          return HttpResult.failed(failure(
            "Cannot check out item that already has an open loan",
            ITEM_BARCODE, barcode));
        }
        else {
          return succeeded(loanAndRelatedRecords);
        }
      }));
  }
}
