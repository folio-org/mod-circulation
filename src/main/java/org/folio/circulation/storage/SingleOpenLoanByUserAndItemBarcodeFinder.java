package org.folio.circulation.storage;

import static org.folio.circulation.domain.validation.CommonFailures.moreThanOneOpenLoanFailure;
import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundForBarcodeFailure;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.validation.BlockRenewalValidator;
import org.folio.circulation.domain.validation.UserNotFoundValidator;
import org.folio.circulation.resources.RenewByBarcodeRequest;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public class SingleOpenLoanByUserAndItemBarcodeFinder {

  public CompletableFuture<Result<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository,
    RequestQueueRepository requestQueueRepository) {

    final Result<RenewByBarcodeRequest> requestResult
      = RenewByBarcodeRequest.from(request);

    final String itemBarcode = requestResult
      .map(RenewByBarcodeRequest::getItemBarcode)
      .orElse("unknown barcode");


    final ItemByBarcodeInStorageFinder itemFinder = new ItemByBarcodeInStorageFinder(
      itemRepository, noItemFoundForBarcodeFailure(itemBarcode));

    final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder
      = new SingleOpenLoanForItemInStorageFinder(loanRepository, userRepository,
      moreThanOneOpenLoanFailure(itemBarcode), false);

    final UserNotFoundValidator userNotFoundValidator = new UserNotFoundValidator(
      userId -> singleValidationError("user is not found", "userId", userId));

    final BlockRenewalValidator blockRenewalValidator =
      new BlockRenewalValidator(requestQueueRepository);

    return requestResult
      .after(checkInRequest -> itemFinder.findItemByBarcode(itemBarcode))
      .thenComposeAsync(itemResult -> itemResult.after(blockRenewalValidator::refuseWhenFirstRequestIsRecall))
      .thenComposeAsync(itemResult -> itemResult.after(singleOpenLoanFinder::findSingleOpenLoan))
      .thenApply(userNotFoundValidator::refuseWhenUserNotFound)
      .thenApply(loanResult -> loanResult.combineToResult(requestResult, this::refuseWhenUserDoesNotMatch));
  }

  private Result<Loan> refuseWhenUserDoesNotMatch(
    Loan loan,
    RenewByBarcodeRequest barcodeRequest) {

    if (userMatches(loan, barcodeRequest.getUserBarcode())) {
      return succeeded(loan);
    } else {
      return failedValidation("Cannot renew item checked out to different user",
        RenewByBarcodeRequest.USER_BARCODE, barcodeRequest.getUserBarcode());
    }
  }

  private boolean userMatches(Loan loan, String expectedUserBarcode) {
    return StringUtils.equals(loan.getUser().getBarcode(), expectedUserBarcode);
  }
}
