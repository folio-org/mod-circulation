package org.folio.circulation.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.domain.validation.CommonFailures.moreThanOneOpenLoanFailure;
import static org.folio.circulation.support.Result.of;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.validation.MoreThanOneLoanValidator;
import org.folio.circulation.domain.validation.NoLoanValidator;
import org.folio.circulation.support.Result;

public class SingleOpenLoanForItemInStorageFinder {
  private final LoanRepository loanRepository;
  private final UserRepository userRepository;
  private final Boolean allowNoLoanToBeFound;

  public SingleOpenLoanForItemInStorageFinder(
    LoanRepository loanRepository,
    UserRepository userRepository,
    boolean allowNoLoanToBeFound) {

    this.loanRepository = loanRepository;
    this.userRepository = userRepository;
    this.allowNoLoanToBeFound = allowNoLoanToBeFound;
  }

  public CompletableFuture<Result<Loan>> findSingleOpenLoan(Item item) {
    //Use same error for no loans and more than one loan to maintain compatibility
    final MoreThanOneLoanValidator moreThanOneLoanValidator
      = new MoreThanOneLoanValidator(moreThanOneOpenLoanFailure(item.getBarcode()));

    final NoLoanValidator noLoanValidator
      = new NoLoanValidator(moreThanOneOpenLoanFailure(item.getBarcode()));

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
  private Function<Result<Optional<Loan>>, Result<Optional<Loan>>> checkForNoLoanIfNeeded(
    NoLoanValidator noLoanValidator, Boolean allowNoLoan) {

    if(allowNoLoan) {
      return  identity();
    }

    return noLoanValidator::failWhenNoLoan;
  }

  private CompletableFuture<Result<Optional<Loan>>> fetchUser(
    Result<Optional<Loan>> result) {

    return result.combineAfter(this::fetchUser,
      (possibleLoan, user) -> possibleLoan.map(
        loan -> loan.withUser(user)));
  }

  private CompletableFuture<Result<User>> fetchUser(
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
