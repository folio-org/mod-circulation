package org.folio.circulation.storage;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.validation.MoreThanOneLoanValidator;
import org.folio.circulation.domain.validation.NoLoanValidator;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.HttpResult;

public class SingleOpenLoanForItemInStorageFinder {
  private final LoanRepository loanRepository;
  private final UserRepository userRepository;
  private final Supplier<HttpFailure> incorrectNumberOfLoansFailureSupplier;

  public SingleOpenLoanForItemInStorageFinder(
    LoanRepository loanRepository,
    UserRepository userRepository,
    Supplier<HttpFailure> incorrectNumberOfLoansFailureSupplier) {

    this.loanRepository = loanRepository;
    this.userRepository = userRepository;
    this.incorrectNumberOfLoansFailureSupplier = incorrectNumberOfLoansFailureSupplier;
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
      .thenApply(noLoanValidator::failWhenNoLoan)
      .thenApply(loanResult -> loanResult.map(loan -> loan.orElse(null)))
      .thenApply(loanResult -> loanResult.map(loan -> loan.withItem(item)))
      .thenComposeAsync(this::fetchUser);
  }

  private CompletableFuture<HttpResult<Loan>> fetchUser(
    HttpResult<Loan> result) {

    return result.combineAfter(userRepository::getUser, Loan::withUser);
  }

  private Optional<Loan> getFirstLoan(MultipleRecords<Loan> loans) {
    return loans.getRecords().stream().findFirst();
  }
}
