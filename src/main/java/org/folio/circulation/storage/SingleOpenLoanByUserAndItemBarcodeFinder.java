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
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ItemRepository;

public class SingleOpenLoanByUserAndItemBarcodeFinder {
  private final LoanRepository loanRepository;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final RequestQueueRepository requestQueueRepository;

  public SingleOpenLoanByUserAndItemBarcodeFinder(
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository,
    RequestQueueRepository requestQueueRepository) {

    this.loanRepository = loanRepository;
    this.itemRepository = itemRepository;
    this.userRepository = userRepository;
    this.requestQueueRepository = requestQueueRepository;
  }

  public CompletableFuture<Result<Loan>> findLoan(
    Result<RenewByBarcodeRequest> request) {

    final String itemBarcode = request
      .map(RenewByBarcodeRequest::getItemBarcode)
      .orElse("unknown barcode");

    final ItemByBarcodeInStorageFinder itemFinder = new ItemByBarcodeInStorageFinder(
      this.itemRepository, noItemFoundForBarcodeFailure(itemBarcode));

    final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder
      = new SingleOpenLoanForItemInStorageFinder(this.loanRepository,
      this.userRepository, moreThanOneOpenLoanFailure(itemBarcode), false);

    final UserNotFoundValidator userNotFoundValidator = new UserNotFoundValidator(
      userId -> singleValidationError("user is not found", "userId", userId));

    final BlockRenewalValidator blockRenewalValidator =
      new BlockRenewalValidator(this.requestQueueRepository);

    return request
      .after(checkInRequest -> itemFinder.findItemByBarcode(itemBarcode))
      .thenComposeAsync(itemResult -> itemResult.after(blockRenewalValidator::refuseWhenFirstRequestIsRecall))
      .thenComposeAsync(itemResult -> itemResult.after(singleOpenLoanFinder::findSingleOpenLoan))
      .thenApply(userNotFoundValidator::refuseWhenUserNotFound)
      .thenApply(loanResult -> loanResult.combineToResult(request, this::refuseWhenUserDoesNotMatch));
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
