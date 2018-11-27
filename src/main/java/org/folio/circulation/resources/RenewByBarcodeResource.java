package org.folio.circulation.resources;

import static org.folio.circulation.domain.validation.CommonFailures.moreThanOneOpenLoanFailure;
import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundForBarcodeFailure;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.validation.UserNotFoundValidator;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
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

    final String itemBarcode = requestResult
      .map(RenewByBarcodeRequest::getItemBarcode)
      .orElse("unknown barcode");

    final ItemByBarcodeInStorageFinder itemFinder = new ItemByBarcodeInStorageFinder(
      itemRepository, noItemFoundForBarcodeFailure(itemBarcode));

    final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder
      = new SingleOpenLoanForItemInStorageFinder(loanRepository, userRepository,
      moreThanOneOpenLoanFailure(itemBarcode));

    //TODO: Possibly move this to single open loan finder?
    final UserNotFoundValidator userNotFoundValidator = new UserNotFoundValidator(
      userId -> failure("user is not found", "userId", userId));

    return requestResult
      .after(checkInRequest -> itemFinder.findItemByBarcode(itemBarcode))
      .thenComposeAsync(itemResult ->
        itemResult.after(singleOpenLoanFinder::findSingleOpenLoan))
      .thenApply(userNotFoundValidator::refuseWhenUserNotFound)
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
