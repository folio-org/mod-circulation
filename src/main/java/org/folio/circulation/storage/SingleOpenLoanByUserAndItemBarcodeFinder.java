package org.folio.circulation.storage;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.validation.UserNotFoundValidator;
import org.folio.circulation.resources.RenewByBarcodeRequest;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

public class SingleOpenLoanByUserAndItemBarcodeFinder {
  private final LoanRepository loanRepository;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;

  public SingleOpenLoanByUserAndItemBarcodeFinder(
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository) {

    this.loanRepository = loanRepository;
    this.itemRepository = itemRepository;
    this.userRepository = userRepository;
  }

  public CompletableFuture<Result<Loan>> findLoan(
    String itemBarcode,
    String userBarcode) {

    final ItemByBarcodeInStorageFinder itemFinder = new ItemByBarcodeInStorageFinder(
      this.itemRepository);

    final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder
      = new SingleOpenLoanForItemInStorageFinder(this.loanRepository,
      this.userRepository, false);

    return itemFinder.findItemByBarcode(itemBarcode)
      .thenComposeAsync(itemResult -> itemResult.after(singleOpenLoanFinder::findSingleOpenLoan))
      .thenApply(UserNotFoundValidator::refuseWhenUserNotFound)
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
