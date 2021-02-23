package org.folio.circulation.domain.anonymization.service;

import static org.folio.circulation.domain.anonymization.LoanAnonymization.FETCH_LOANS_PAGE_LIMIT;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class LoansForBorrowerFinder extends DefaultLoansFinder {
  private final LoanRepository loanRepository;
  private final String userId;

  public LoansForBorrowerFinder(Clients clients, String userId, LoanRepository loanRepository) {
    super(new AccountRepository(clients));
    this.userId = userId;
    this.loanRepository = loanRepository;
  }

  @Override
  public CompletableFuture<Result<Collection<Loan>>> findLoansToAnonymize() {
    return loanRepository.findClosedLoans(userId, FETCH_LOANS_PAGE_LIMIT)
      .thenCompose(this::fetchAdditionalLoanInfo);
  }
}
