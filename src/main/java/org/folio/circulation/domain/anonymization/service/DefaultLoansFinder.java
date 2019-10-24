package org.folio.circulation.domain.anonymization.service;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

abstract class DefaultLoansFinder implements LoanAnonymizationFinderService {

  private final AccountRepository accountRepository;
  protected Clients clients;

  DefaultLoansFinder(Clients clients) {
    this.clients = clients;
    accountRepository = new AccountRepository(clients);
  }

  CompletableFuture<Result<Collection<Loan>>> fetchAdditionalLoanInfo(
      Result<MultipleRecords<Loan>> records) {

    return records.after(accountRepository::findAccountsForLoans)
      .thenCompose(r -> completedFuture(r.map(MultipleRecords::getRecords)));
  }
}