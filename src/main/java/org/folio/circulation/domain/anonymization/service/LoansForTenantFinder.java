package org.folio.circulation.domain.anonymization.service;

import static org.folio.circulation.support.http.client.PageLimit.limit;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.results.Result;

public class LoansForTenantFinder extends DefaultLoansFinder {
  private final LoanRepository loanRepository;
  private final int numberOfLoansToCheck;

  public LoansForTenantFinder(LoanRepository loanRepository,
    AccountRepository accountRepository, int numberOfLoansToCheck) {

    super(accountRepository);
    this.loanRepository = loanRepository;
    this.numberOfLoansToCheck = numberOfLoansToCheck;
  }

  public CompletableFuture<Result<Collection<Loan>>> findLoansToAnonymize() {
    return loanRepository.findLoansToAnonymize(limit(numberOfLoansToCheck))
      .thenCompose(this::fetchAdditionalLoanInfo);
  }
}
