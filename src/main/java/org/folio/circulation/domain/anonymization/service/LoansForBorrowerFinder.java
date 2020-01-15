package org.folio.circulation.domain.anonymization.service;

import static org.folio.circulation.domain.anonymization.LoanAnonymization.FETCH_LOANS_LIMIT;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

public class LoansForBorrowerFinder extends DefaultLoansFinder {

  private final LoanRepository loanRepository;
  private String userId;

  public LoansForBorrowerFinder(Clients clients, String userId) {
    super(clients);
    this.userId = userId;
    loanRepository = new LoanRepository(clients);
  }

  @Override
  public CompletableFuture<Result<Collection<Loan>>> findLoansToAnonymize() {

    return loanRepository.findClosedLoans(userId, FETCH_LOANS_LIMIT)
      .thenCompose(this::fetchAdditionalLoanInfo);
  }
}
