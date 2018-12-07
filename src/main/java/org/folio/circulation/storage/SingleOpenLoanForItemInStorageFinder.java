package org.folio.circulation.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.HttpResult.of;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.validation.MoreThanOneLoanValidator;
import org.folio.circulation.domain.validation.NoLoanValidator;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.HttpResult;

public class SingleOpenLoanForItemInStorageFinder {
  private final LoanRepository loanRepository;
  private final UserRepository userRepository;
  private final Supplier<HttpFailure> incorrectNumberOfLoansFailureSupplier;
  private final Boolean allowNoLoanToBeFound;

  public SingleOpenLoanForItemInStorageFinder(
    LoanRepository loanRepository,
    UserRepository userRepository,
    Supplier<HttpFailure> incorrectNumberOfLoansFailureSupplier,
    boolean allowNoLoanToBeFound) {

    this.loanRepository = loanRepository;
    this.userRepository = userRepository;
    this.incorrectNumberOfLoansFailureSupplier = incorrectNumberOfLoansFailureSupplier;
    this.allowNoLoanToBeFound = allowNoLoanToBeFound;
  }

  public CompletableFuture<HttpResult<Loan>> findSingleOpenLoan(Item item) {
    //Use same error for no loans and more than one loan to maintain compatibility
    final MoreThanOneLoanValidator moreThanOneLoanValidator
      = new MoreThanOneLoanValidator(incorrectNumberOfLoansFailureSupplier);

    final NoLoanValidator noLoanValidator
      = new NoLoanValidator(incorrectNumberOfLoansFailureSupplier);

    return loanRepository.findOpenLoans(item)
      .thenApply(moreThanOneLoanValidator::failWhenMoreThanOneLoan)
      .thenApply(loanResult -> loanResult.map(this::getFirstLoan))
      .thenApply(checkForNoLoanIfNeeded(noLoanValidator, allowNoLoanToBeFound))
      .thenApply(loanResult -> loanResult.map(loan -> mapPossibleSingleLoan(loan, item)))
      .thenComposeAsync(this::fetchUser)
      .thenApply(loanResult -> loanResult.map(possibleLoan -> possibleLoan.orElse(null)));
  }

  private Optional<Loan> mapPossibleSingleLoan(
    Optional<Loan> optionalLoan,
    Item item) {

    return optionalLoan.map(loan -> loan.withItem(item));
  }

  //TODO: Improve how this is made optional
  private Function<HttpResult<Optional<Loan>>, HttpResult<Optional<Loan>>> checkForNoLoanIfNeeded(
    NoLoanValidator noLoanValidator, Boolean allowNoLoan) {

    if(allowNoLoan) {
      return  identity();
    }

    return noLoanValidator::failWhenNoLoan;
  }

  private CompletableFuture<HttpResult<Optional<Loan>>> fetchUser(
    HttpResult<Optional<Loan>> result) {

    return result.combineAfter(this::fetchUser,
      (possibleLoan, user) -> possibleLoan.map(
        loan -> loan.withUser(user)));
  }

  private CompletableFuture<HttpResult<User>> fetchUser(
    Optional<Loan> possibleLoan) {

    if(!possibleLoan.isPresent()) {
      return completedFuture(of(() -> null));
    }

    return userRepository.getUser(possibleLoan.get());
  }

  private Optional<Loan> getFirstLoan(MultipleRecords<Loan> loans) {
    return loans.getRecords().stream().findFirst();
  }
}
