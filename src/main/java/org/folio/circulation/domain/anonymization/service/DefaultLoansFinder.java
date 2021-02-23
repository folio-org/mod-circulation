package org.folio.circulation.domain.anonymization.service;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.support.results.Result;

abstract class DefaultLoansFinder implements LoanAnonymizationFinderService {
  private final AccountRepository accountRepository;

  DefaultLoansFinder(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  CompletableFuture<Result<Collection<Loan>>> fetchAdditionalLoanInfo(
      Result<MultipleRecords<Loan>> records) {

    return records.after(accountRepository::findAccountsForLoans)
      .thenCompose(r -> completedFuture(r.map(MultipleRecords::getRecords)));
  }
}
