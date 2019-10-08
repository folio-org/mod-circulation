package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.CqlQuery.exactMatch;
import static org.folio.circulation.support.Result.succeeded;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;

public class AccountRepository {

  private static final String LOAN_ID_FIELD_NAME = "loanId";
  private final CollectionResourceClient accountsStorageClient;

  public AccountRepository(Clients clients) {
    accountsStorageClient = clients.accountsStorageClient();
  }

  public CompletableFuture<Result<Loan>> findAccountsForLoan(Result<Loan> loanResult) {
    return loanResult.after(loan -> {
      if (loan == null) {
        return completedFuture(loanResult);
      }
      return loanResult.combineAfter(r -> fetchAccountsForLoan(loan.getId()), Loan::withAccounts);
    });
  }

  private CompletableFuture<Result<Collection<Account>>> fetchAccountsForLoan(String loanId) {

    return createAccountsFetcher().findByQuery(exactMatch(LOAN_ID_FIELD_NAME, loanId))
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findAccountsForLoans(
    MultipleRecords<Loan> multipleLoans) {

    if (multipleLoans.getRecords().isEmpty()) {
      return completedFuture(succeeded(multipleLoans));
    }

    return getAccountsForLoans(multipleLoans.getRecords())
      .thenApply(r -> r.map(accountMap -> multipleLoans.mapRecords(
        loan -> loan.withAccounts(accountMap.getOrDefault(loan.getId(),
          new ArrayList<>())))));
  }

  private CompletableFuture<Result<Map<String, List<Account>>>> getAccountsForLoans(Collection<Loan> loans) {

    final Collection<String> loanIds =
      loans.stream()
        .filter(Objects::nonNull)
        .map(Loan::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    return createAccountsFetcher().findByIndexName(loanIds, LOAN_ID_FIELD_NAME)
      .thenComposeAsync(r -> r.after(multipleRecords -> completedFuture(succeeded(
        multipleRecords.getRecords().stream().collect(
          Collectors.groupingBy(Account::getLoanId))))));
  }

  private MultipleRecordFetcher<Account> createAccountsFetcher() {
    return new MultipleRecordFetcher<>(accountsStorageClient, "accounts", Account::from);
  }
}
