package org.folio.circulation.resources;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

public class RenewByBarcodeResource extends RenewalResource {
  public RenewByBarcodeResource(HttpClient client) {
    super(client, "/circulation/renew-by-barcode");
  }

  @Override
  protected CompletableFuture<HttpResult<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository) {

    final HttpResult<RenewByBarcodeRequest> requestResult
      = RenewByBarcodeRequest.from(request);

    return requestResult
      .after(query -> loanRepository.findOpenLoanByBarcode(query.getItemBarcode()))
      .thenApply(loanResult -> loanResult.combineToResult(requestResult,
        this::refuseWhenUserDoesNotMatch));
  }

  private HttpResult<Loan> refuseWhenUserDoesNotMatch(
    Loan loan,
    RenewByBarcodeRequest barcodeRequest) {

    if(userMatches(loan, barcodeRequest.getUserBarcode())) {
      return succeeded(loan);
    }
    else {
      return failed(failure("Cannot renew item checked out to different user",
        RenewByBarcodeRequest.USER_BARCODE, barcodeRequest.getUserBarcode()));
    }
  }

  private boolean userMatches(Loan loan, String expectedUserBarcode) {
    return StringUtils.equals(loan.getUser().getBarcode(), expectedUserBarcode);
  }
}
