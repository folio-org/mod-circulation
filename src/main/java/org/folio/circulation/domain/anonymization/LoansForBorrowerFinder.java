package org.folio.circulation.domain.anonymization;

import static org.folio.circulation.domain.anonymization.LoanAnonymizationHelper.*;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.Result;

public class LoansForBorrowerFinder extends DefaultLoansFinder {

  private final LoanRepository loanRepository;
  private String userId;

  public LoansForBorrowerFinder(LoanAnonymizationHelper anonymization, String userId) {
    super(anonymization);
    this.userId = userId;
    loanRepository = new LoanRepository(anonymization.clients());
  }

  @Override
  public CompletableFuture<Result<Collection<Loan>>> findLoansToAnonymize() {

    return loanRepository.findClosedLoans(userId, FETCH_LOANS_LIMIT)
      .thenCompose(this::fillLoanInformation);
  }
}
