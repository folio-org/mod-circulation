package org.folio.circulation.domain.anonymization.service;

import static org.folio.circulation.support.http.client.PageLimit.limit;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.results.Result;

public class LoansForBorrowerFinder extends DefaultLoansFinder {
  private final LoanRepository loanRepository;
  private final String userId;

  public LoansForBorrowerFinder(String userId, LoanRepository loanRepository,
    AccountRepository accountRepository) {

    super(accountRepository);
    this.userId = userId;
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<Collection<Loan>>> findLoansToAnonymize() {
    return loanRepository.findClosedLoans(userId, limit(5000))
      .thenCompose(this::fetchAdditionalLoanInfo);
  }
}
