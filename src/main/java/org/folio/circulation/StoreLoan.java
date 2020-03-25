package org.folio.circulation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.Result;

public class StoreLoan {
  private final LoanRepository loanRepository;

  public StoreLoan(LoanRepository loanRepository) {
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<Loan>> updateLoanInStorage(Loan loan) {
    if (loan == null) {
      return completedFuture(succeeded(null));
    }

    return loanRepository.updateLoan(loan);
  }
}
