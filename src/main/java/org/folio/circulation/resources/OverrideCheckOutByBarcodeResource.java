package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;

import io.vertx.core.http.HttpClient;

public class OverrideCheckOutByBarcodeResource extends CheckOutByBarcodeResource {

  public OverrideCheckOutByBarcodeResource(HttpClient client) {
    super(client, "/circulation/override-check-out-by-barcode");
  }

  @Override
  CompletableFuture<Result<LoanAndRelatedRecords>> applyLoanPolicy(LoanAndRelatedRecords relatedRecords,
                                                                   ClosedLibraryStrategyService strategyService,
                                                                   String dueDate,
                                                                   String comment) {

    return completedFuture(succeeded(relatedRecords))
      .thenApply(r -> r.next(this::refuseWhenItemIsLoanable))
      .thenApply(r -> r.next(records -> changeDueDate(records, dueDate)))
      .thenApply(r -> r.next(records -> changeStatus(records, comment)));
  }

  private Result<LoanAndRelatedRecords> refuseWhenItemIsLoanable(LoanAndRelatedRecords relatedRecords) {
    if (relatedRecords.getLoanPolicy().isNotLoanable()) {
      String itemBarcode = relatedRecords.getLoan().getItem().getBarcode();
      return failed(singleValidationError("Override is not allowed when item is loanable", ITEM_BARCODE, itemBarcode));
    }
    return succeeded(relatedRecords);
  }

  private Result<LoanAndRelatedRecords> changeDueDate(LoanAndRelatedRecords loanAndRelatedRecords, String dueDate) {
    loanAndRelatedRecords.getLoan().changeDueDate(DateTime.parse(dueDate));
    return succeeded(loanAndRelatedRecords);
  }

  private Result<LoanAndRelatedRecords> changeStatus(LoanAndRelatedRecords loanAndRelatedRecords, String comment) {
    Loan loan = loanAndRelatedRecords.getLoan();
    loan.changeAction("checkedoutThroughOverride");
    loan.changeActionComment(comment);
    return succeeded(loanAndRelatedRecords);
  }


}
