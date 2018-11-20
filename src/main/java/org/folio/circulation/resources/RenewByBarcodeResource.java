package org.folio.circulation.resources;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.HttpResult;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

public class RenewByBarcodeResource extends RenewalResource {
  public RenewByBarcodeResource(HttpClient client) {
    super(client, "/circulation/renew-by-barcode");
  }

  @Override
  protected CompletableFuture<HttpResult<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository) {

    final HttpResult<RenewByBarcodeRequest> requestResult = RenewByBarcodeRequest.from(request);

    return requestResult
      .after(loanRepository::findOpenLoanByBarcode)
      .thenApply(loanResult -> loanResult.combineToResult(requestResult,
        this::refuseWhenUserDoesNotMatch));
  }

  private HttpResult<Loan> refuseWhenUserDoesNotMatch(
    Loan loan,
    RenewByBarcodeRequest barcodeRequest) {
    
    if(barcodeRequest.userMatches(loan.getUser())) {
      return succeeded(loan);
    }
    else {
      return failed(barcodeRequest.userDoesNotMatchError());
    }
  }

}
