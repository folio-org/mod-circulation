package org.folio.circulation.storage;

import static org.folio.circulation.domain.validation.CommonFailures.moreThanOneOpenLoanFailure;
import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundForBarcodeFailure;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.validation.BlockRenewalValidator;
import org.folio.circulation.domain.validation.UserNotFoundValidator;
import org.folio.circulation.resources.RenewByBarcodeRequest;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

public class SingleOpenLoanByUserAndItemBarcodeFinder {
  private final LoanRepository loanRepository;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final RequestRepository requestRepository;

  public SingleOpenLoanByUserAndItemBarcodeFinder(
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository,
    RequestRepository requestRepository) {

    this.loanRepository = loanRepository;
    this.itemRepository = itemRepository;
    this.userRepository = userRepository;
    this.requestRepository = requestRepository;
  }

  public CompletableFuture<Result<Loan>> findLoan(
    String itemBarcode,
    String userBarcode) {

    final ItemByBarcodeInStorageFinder itemFinder = new ItemByBarcodeInStorageFinder(
      this.itemRepository, noItemFoundForBarcodeFailure(itemBarcode));

    final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder
      = new SingleOpenLoanForItemInStorageFinder(this.loanRepository,
      this.userRepository, moreThanOneOpenLoanFailure(itemBarcode), false);

    final UserNotFoundValidator userNotFoundValidator = new UserNotFoundValidator(
      userId -> singleValidationError("user is not found", "userId", userId));

    final BlockRenewalValidator blockRenewalValidator =
      new BlockRenewalValidator(requestRepository);

    return itemFinder.findItemByBarcode(itemBarcode)
      .thenComposeAsync(itemResult -> itemResult.after(blockRenewalValidator::refuseWhenFirstRequestIsRecall))
      .thenComposeAsync(itemResult -> itemResult.after(singleOpenLoanFinder::findSingleOpenLoan))
      .thenApply(userNotFoundValidator::refuseWhenUserNotFound)
      .thenComposeAsync(loanResult -> loanResult.after(refuseWhenUserDoesNotMatch(userBarcode)));
  }

  private Function<Loan, CompletableFuture<Result<Loan>>> refuseWhenUserDoesNotMatch(
    String userBarcode) {

    return loan -> refuseWhenUserDoesNotMatch(loan, userBarcode);
  }

  private CompletableFuture<Result<Loan>> refuseWhenUserDoesNotMatch(
    Loan loan,
    String userBarcode) {

    if (userMatches(loan, userBarcode)) {
      return CompletableFuture.completedFuture(succeeded(loan));
    } else {
      return CompletableFuture.completedFuture(
        failedValidation("Cannot renew item checked out to different user",
        RenewByBarcodeRequest.USER_BARCODE, userBarcode));
    }
  }

  private boolean userMatches(Loan loan, String expectedUserBarcode) {
    return StringUtils.equals(loan.getUser().getBarcode(), expectedUserBarcode);
  }
}
