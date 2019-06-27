package org.folio.circulation.domain;

import org.folio.circulation.support.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.CqlQuery.exactMatch;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.Result.succeeded;

public class AccountRepository {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient accountsStorageClient;

  private final Result<CqlQuery> accountStatusQuery = exactMatch("status.name", "Open");

  public AccountRepository(Clients clients) {
    accountsStorageClient = clients.accountsStorageClient();
  }

  public CompletableFuture<Result<Loan>> findAccountsForLoan(Result<Loan> loanResult) {
    return loanResult.after(loan -> {
      if (loan == null) {
        return completedFuture(loanResult);
      }
      return loanResult
        .combineAfter(r -> getAccountsForLoan(loan.getId()),
          Loan::withAccounts);
    });
  }

  private CompletableFuture<Result<Collection<Account>>> getAccountsForLoan(String loanId) {

    final Result<CqlQuery> loanIdQuery = exactMatch("loanId", loanId);

    return createAccountsFetcher().findByQuery
      (accountStatusQuery.combine(loanIdQuery, CqlQuery::and))
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findAccountsForLoans(
    MultipleRecords<Loan> multipleLoans) {

    if (multipleLoans.getRecords().isEmpty()) {
      return completedFuture(succeeded(multipleLoans));
    }

    return getAccountsForLoans(multipleLoans.getRecords())
      .thenApply(r -> r.map(accountMap -> multipleLoans.mapRecords(
        loan -> loan.withAccounts(accountMap.getOrDefault(loan.getId(), null)))));
  }

  private CompletableFuture<Result<Map<String, List<Account>>>> getAccountsForLoans(
    Collection<Loan> loans) {

    final Collection<String> accountsToFetch =
      loans.stream()
        .filter(Objects::nonNull)
        .map(Loan::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    final MultipleRecordFetcher<Account> fetcher = createAccountsFetcher();
    final Result<CqlQuery> loanIdQuery = exactMatchAny("loanId", accountsToFetch);

    return createAccountsFetcher()
      .findByQuery(accountStatusQuery.combine(loanIdQuery, CqlQuery::and))
      .thenComposeAsync(r -> r.after(multipleRecords -> completedFuture(succeeded(
        multipleRecords.getRecords().stream().collect(
          Collectors.groupingBy(Account::getLoanId))
        ))
        )
      );
  }

  private MultipleRecordFetcher<Account> createAccountsFetcher() {
    return new MultipleRecordFetcher<>(accountsStorageClient, "accounts", Account::from);
  }
}
