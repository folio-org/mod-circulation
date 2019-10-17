package org.folio.circulation.domain.anonymization;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Result;

abstract class DefaultLoansFinder implements LoanAnonymizationFinderService {

  private final AccountRepository accountRepository;
  protected LoanAnonymizationHelper anonymization;

  DefaultLoansFinder(LoanAnonymizationHelper anonymization) {
    this.anonymization = anonymization;
    accountRepository = new AccountRepository(anonymization.clients());
  }

  CompletableFuture<Result<Collection<Loan>>> fetchAdditionalLoanInfo(
      Result<MultipleRecords<Loan>> records) {

    return records.after(accountRepository::findAccountsForLoans)
      .thenCompose(r -> completedFuture(r.map(MultipleRecords::getRecords)));
  }

}
