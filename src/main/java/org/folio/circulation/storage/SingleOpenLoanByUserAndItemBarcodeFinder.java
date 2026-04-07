package org.folio.circulation.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.validation.UserNotFoundValidator;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.renewal.RenewByBarcodeRequest;
import org.folio.circulation.support.results.Result;

public class SingleOpenLoanByUserAndItemBarcodeFinder {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
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

  public CompletableFuture<Result<Loan>> findLoan(String itemBarcode, String userBarcode) {
    log.debug("findLoan:: parameters itemBarcode: {}, userBarcode: {}", itemBarcode, userBarcode);
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

  private CompletableFuture<Result<Loan>> refuseWhenUserDoesNotMatch(Loan loan,
    String userBarcode) {

    if (userMatches(loan, userBarcode)) {
      return completedFuture(succeeded(loan));
    } else {
      log.info("refuseWhenUserDoesNotMatch:: item is checked out to a different user, loanId: {}",
        loan::getId);
      return completedFuture(failedValidation("Cannot renew item checked out to different user",
        RenewByBarcodeRequest.USER_BARCODE, userBarcode));
    }
  }

  private boolean userMatches(Loan loan, String expectedUserBarcode) {
    return StringUtils.equals(loan.getUser().getBarcode(), expectedUserBarcode);
  }
}
