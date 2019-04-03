package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.support.Result;

import io.vertx.core.http.HttpClient;

public class RegularCheckOutByBarcodeResource extends CheckOutByBarcodeResource {

  public RegularCheckOutByBarcodeResource(HttpClient client) {
    super(client, "/circulation/check-out-by-barcode");
  }

  @Override
  CompletableFuture<Result<LoanAndRelatedRecords>> applyLoanPolicy(LoanAndRelatedRecords relatedRecords,
                                                                   ClosedLibraryStrategyService strategyService,
                                                                   String dueDate,
                                                                   String comment) {
    return completedFuture(succeeded(relatedRecords))
      .thenApply(r -> r.next(this::refuseWhenItemIsNotLoanable))
      .thenApply(r -> r.next(this::calculateDefaultInitialDueDate))
      .thenCompose(r -> r.after(strategyService::applyCLDDM));
  }

  private Result<LoanAndRelatedRecords> refuseWhenItemIsNotLoanable(LoanAndRelatedRecords relatedRecords) {
    if (relatedRecords.getLoanPolicy().isNotLoanable()) {
      String itemBarcode = relatedRecords.getLoan().getItem().getBarcode();
      return failed(singleValidationError("Item is not loanable", ITEM_BARCODE, itemBarcode));
    }
    return succeeded(relatedRecords);
  }

  private Result<LoanAndRelatedRecords> calculateDefaultInitialDueDate(LoanAndRelatedRecords loanAndRelatedRecords) {
    Loan loan = loanAndRelatedRecords.getLoan();
    LoanPolicy loanPolicy = loanAndRelatedRecords.getLoanPolicy();
    return loanPolicy.calculateInitialDueDate(loan)
      .map(dueDate -> {
        loanAndRelatedRecords.getLoan().changeDueDate(dueDate);
        return loanAndRelatedRecords;
      });
  }
}
