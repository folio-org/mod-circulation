package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.CqlQuery.exactMatch;
import static org.folio.circulation.support.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountRepository {

  private final CollectionResourceClient accountsStorageClient;
  private static final Result<CqlQuery> openAccountStatusQuery =
    exactMatch("status.name", "Open");


  public AccountRepository(Clients clients) {
    accountsStorageClient = clients.accountsStorageClient();
  }

  public CompletableFuture<Result<Loan>> findOpenAccountsForLoan(Result<Loan> loanResult) {
    return loanResult.after(loan -> {
      if (loan == null) {
        return completedFuture(loanResult);
      }
      return loanResult
        .combineAfter(r -> fetchOpenAccountsForLoan(loan.getId()),
          Loan::withAccounts);
    });
  }

  private CompletableFuture<Result<Collection<Account>>> fetchOpenAccountsForLoan(
    String loanId) {

    return createAccountsFetcher().findByQuery(
      openAccountStatusQuery.combine(exactMatch("loanId", loanId), CqlQuery::and))
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findOpenAccountsForLoans(
    MultipleRecords<Loan> multipleLoans) {

    if (multipleLoans.getRecords().isEmpty()) {
      return completedFuture(succeeded(multipleLoans));
    }

    return getAccountsForLoans(multipleLoans.getRecords())
      .thenApply(r -> r.map(accountMap -> multipleLoans.mapRecords(
        loan -> loan.withAccounts(accountMap.getOrDefault(loan.getId(),
          new ArrayList<>())))));
  }

  private CompletableFuture<Result<Map<String, List<Account>>>> getAccountsForLoans(
    Collection<Loan> loans) {

    final Collection<String> accountsToFetch =
      loans.stream()
        .filter(Objects::nonNull)
        .map(Loan::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    return createAccountsFetcher()
      .findByIndexNameAndQuery(accountsToFetch, "loanId", openAccountStatusQuery)
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
