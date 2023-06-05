package org.folio.circulation.infrastructure.storage.feesandfines;

import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria.byIndex;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.representations.StoredAccount;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.ResponseInterpreter;

public class AccountRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String LOAN_ID_FIELD_NAME = "loanId";
  private static final String ACCOUNT_ID_FIELD_NAME = "accountId";
  private static final String ACCOUNTS_COLLECTION_PROPERTY_NAME = "accounts";

  private final CollectionResourceClient accountsStorageClient;
  private final GetManyRecordsClient feefineActionsStorageClient;

  public AccountRepository(Clients clients) {
    accountsStorageClient = clients.accountsStorageClient();
    feefineActionsStorageClient = clients.feeFineActionsStorageClient();
  }

  public CompletableFuture<Result<Loan>> findAccountsAndActionsForLoan(Result<Loan> loanResult) {
    log.debug("findAccountsAndActionsForLoan:: parameters loanResult: {}", () -> resultAsString(loanResult));
    return loanResult.after(loan -> {
      if (loan == null) {
        log.info("findAccountsAndActionsForLoan:: loan is null");
        return completedFuture(loanResult);
      }
      return loanResult.combineAfter(r -> fetchAccountsAndActionsForLoan(loan.getId()),
        Loan::withAccounts);
    });
  }

  public CompletableFuture<Result<Collection<Account>>> findAccountsAndActionsForLoanByQuery(
    Result<CqlQuery> queryResult) {

    log.debug("findAccountsAndActionsForLoanByQuery:: parameters queryResult: {}", () -> resultAsString(queryResult));

    return findWithCqlQuery(accountsStorageClient, ACCOUNTS_COLLECTION_PROPERTY_NAME, Account::from)
      .findByQuery(queryResult)
      .thenCompose(r -> r.after(this::findFeeFineActionsForAccounts))
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  public CompletableFuture<Result<Loan>> findAccountsForLoan(Loan loan) {
    log.debug("findAccountsForLoan:: parameters loan: {}", loan);
    return findWithCqlQuery(accountsStorageClient, ACCOUNTS_COLLECTION_PROPERTY_NAME, Account::from)
      .findByQuery(exactMatch(LOAN_ID_FIELD_NAME, loan.getId()))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(loan::withAccounts));
  }

  public CompletableFuture<Result<Collection<Account>>> findAccountsForLoanByQuery(Loan loan,
    Result<CqlQuery> query) {

    log.debug("findAccountsForLoanByQuery:: parameters loan: {}, query: {}", () -> loan, () -> resultAsString(query));

    return findWithCqlQuery(accountsStorageClient, ACCOUNTS_COLLECTION_PROPERTY_NAME, Account::from)
      .findByQuery(exactMatch(LOAN_ID_FIELD_NAME, loan.getId()).combine(query, CqlQuery::and))
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  private CompletableFuture<Result<Collection<Account>>> fetchAccountsAndActionsForLoan(String loanId) {
    return findAccountsAndActionsForLoanByQuery(exactMatch(LOAN_ID_FIELD_NAME, loanId));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findAccountsForLoans(
    MultipleRecords<Loan> multipleLoans) {

    log.debug("findAccountsForLoans:: parameters multipleLoans: {}",() -> multipleRecordsAsString(multipleLoans));

    if (multipleLoans.getRecords().isEmpty()) {
      log.info("findAccountsForLoans:: multipleLoans is empty");
      return completedFuture(succeeded(multipleLoans));
    }

    return getAccountsForLoans(multipleLoans.getRecords())
      .thenApply(r -> r.map(accountMap -> multipleLoans.mapRecords(
        loan -> loan.withAccounts(accountMap.getOrDefault(loan.getId(),
          new ArrayList<>())))));
  }

  private CompletableFuture<Result<Map<String, List<Account>>>> getAccountsForLoans(Collection<Loan> loans) {
    log.debug("getAccountsForLoans:: parameters loans: {}", () -> collectionAsString(loans));

    final Set<String> loanIds =
      loans.stream()
        .filter(Objects::nonNull)
        .map(Loan::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    return findWithMultipleCqlIndexValues(accountsStorageClient,
        ACCOUNTS_COLLECTION_PROPERTY_NAME, Account::from)
      .find(byIndex(LOAN_ID_FIELD_NAME, loanIds))
      .thenCompose(r -> r.after(this::findFeeFineActionsForAccounts))
      .thenComposeAsync(r -> r.after(multipleRecords -> completedFuture(succeeded(multipleRecords.getRecords()
        .stream()
        .collect(Collectors.groupingBy(Account::getLoanId))))));
  }

  public CompletableFuture<Result<MultipleRecords<Account>>> findFeeFineActionsForAccounts(
      MultipleRecords<Account> multipleAccounts) {

    log.debug("findFeeFineActionsForAccounts:: parameters multipleAccounts: {}",
      () -> multipleRecordsAsString(multipleAccounts));

    if (multipleAccounts.getRecords().isEmpty()) {
      log.info("findFeeFineActionsForAccounts:: multipleAccounts is empty");
      return completedFuture(succeeded(multipleAccounts));
    }

    return getFeeFineActionsForAccounts(multipleAccounts.getRecords())
        .thenApply(r -> r.map(accountMap -> multipleAccounts.mapRecords(
            account -> account.withFeeFineActions(accountMap.getOrDefault(account.getId(),
                new ArrayList<>())))));
  }

  private CompletableFuture<Result<Map<String, List<FeeFineAction>>>> getFeeFineActionsForAccounts(
    Collection<Account> accounts) {

    log.debug("getFeeFineActionsForAccounts:: parameters accounts: {}", () -> collectionAsString(accounts));

    final Set<String> loanIds =
    accounts.stream()
      .filter(Objects::nonNull)
      .map(Account::getId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    return createFeeFineActionFetcher().find(byIndex(ACCOUNT_ID_FIELD_NAME, loanIds))
        .thenComposeAsync(r -> r.after(multipleRecords -> completedFuture(succeeded(
            multipleRecords.getRecords().stream().collect(
                Collectors.groupingBy(FeeFineAction::getAccountId))))));
  }

  private FindWithMultipleCqlIndexValues<FeeFineAction> createFeeFineActionFetcher() {
    return findWithMultipleCqlIndexValues(feefineActionsStorageClient,
      "feefineactions", FeeFineAction::from);
  }

  public CompletableFuture<Result<Account>> findAccountForAction(FeeFineAction action) {
    log.debug("findAccountForAction:: parameters action: {}", action);
    if (isNull(action)) {
      log.info("findAccountForAction:: action is null");
      return ofAsync(() -> null);
    }

    return findById(action.getAccountId());
  }

  public CompletableFuture<Result<Account>> findById(String id) {
    log.debug("findById:: parameters id: {}", id);
    if (isNull(id)) {
      log.info("findById:: id is null");
      return ofAsync(() -> null);
    }

    return FetchSingleRecord.<Account>forRecord("account")
      .using(accountsStorageClient)
      .mapTo(Account::from)
      .whenNotFound(failed(new RecordNotFoundFailure("account", id)))
      .fetch(id);
  }

  public CompletableFuture<Result<Account>> create(StoredAccount account) {
    log.debug("create:: parameters account: {}", account);
    final ResponseInterpreter<Account> interpreter = new ResponseInterpreter<Account>()
      .flatMapOn(201, mapUsingJson(Account::from))
      .otherwise(forwardOnFailure());

    return accountsStorageClient.post(account)
      .thenApply(interpreter::flatMap);
  }

  public CompletableFuture<Result<Void>> update(StoredAccount account) {
    log.debug("update:: parameters account: {}", account);
    final ResponseInterpreter<Void> interpreter = new ResponseInterpreter<Void>()
      .on(204, succeeded(null))
      .otherwise(forwardOnFailure());

    return accountsStorageClient.put(account.getId(), account)
      .thenApply(interpreter::flatMap);
  }
}
