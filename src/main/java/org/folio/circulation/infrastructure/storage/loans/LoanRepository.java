package org.folio.circulation.infrastructure.storage.loans;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.domain.representations.LoanProperties.BORROWER;
import static org.folio.circulation.domain.representations.LoanProperties.DUE_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.FEESANDFINES;
import static org.folio.circulation.domain.representations.LoanProperties.LOAN_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.LOAN_POLICY;
import static org.folio.circulation.domain.representations.LoanProperties.LOST_ITEM_POLICY;
import static org.folio.circulation.domain.representations.LoanProperties.OVERDUE_FINE_POLICY;
import static org.folio.circulation.domain.representations.LoanProperties.PATRON_GROUP_AT_CHECKOUT;
import static org.folio.circulation.domain.representations.LoanProperties.PATRON_GROUP_ID_AT_CHECKOUT;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.CqlSortBy.descending;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.http.client.CqlQuery.notIn;
import static org.folio.circulation.support.http.client.PageLimit.one;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAction;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanHistory;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.policy.Policy;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.fetching.GetManyRecordsRepository;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class LoanRepository implements GetManyRecordsRepository<Loan> {
  private static final String RECORDS_PROPERTY_NAME = "loans";
  public static final String DUE_DATE_CHANGED_BY_HOLD = "dueDateChangedByHold";

  private final CollectionResourceClient loansStorageClient;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final LoanHistoryRepository loanHistoryRepository;
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String ITEM_STATUS = "itemStatus";
  private static final String ITEM_ID = "itemId";

  private static final String ID = "id";
  private static final String USER_ID = "userId";

  private static final String IS_DCB = "isDcb";
  private static final String DCB_USER_LASTNAME = "DcbSystem";

  public LoanRepository(Clients clients, ItemRepository itemRepository,
    UserRepository userRepository) {

    loansStorageClient = clients.loansStorage();
    this.itemRepository = itemRepository;
    this.userRepository = userRepository;
    this.loanHistoryRepository = new LoanHistoryRepository(clients);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> createLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    log.debug("createLoan:: parameters loanAndRelatedRecords: {}", loanAndRelatedRecords);

    final Loan loan = loanAndRelatedRecords.getLoan();

    JsonObject storageLoan = mapToStorageRepresentation(loan, loan.getItem());

    final ResponseInterpreter<Loan> interpreter = new ResponseInterpreter<Loan>()
      .flatMapOn(201, mapUsingJson(loan::replaceRepresentation))
      .otherwise(forwardOnFailure());

    return loansStorageClient.post(storageLoan)
      .thenApply(interpreter::flatMap)
      .thenApply(mapResult(loanAndRelatedRecords::withLoan));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> updateLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    log.debug("updateLoan:: parameters loanAndRelatedRecords: {}", loanAndRelatedRecords);
    Loan loan = loanAndRelatedRecords.getLoan();
    log.info("Loan " + loan.getId() + " prior to update:  " + loan.asJson().toString());
    return updateLoan(loanAndRelatedRecords.getLoan())
      .thenApply(mapResult(loanAndRelatedRecords::withLoan));
  }

  public CompletableFuture<Result<Loan>> updateLoan(Loan loan) {
    log.debug("updateLoan:: parameters loan: {}", loan);
    if (loan == null) {
      log.info("updateLoan:: loan is null");
      return completedFuture(of(() -> null));
    }
    JsonObject storageLoan = mapToStorageRepresentation(loan, loan.getItem());

    return loansStorageClient.put(loan.getId(), storageLoan)
      .thenApply(r -> r.map(response -> loan));
  }

  /**
   *
   * @param request the request to fetch the open loan for the same item for
   * @return  success with loan if one found,
   * success with null if the no open loan is found,
   * failure if more than one open loan for the item found
   */
  public CompletableFuture<Result<Loan>> findOpenLoanForRequest(Request request) {
    log.debug("findOpenLoanForRequest:: parameters request: {}", request);
    if (!request.hasItemId()) {
      log.info("findOpenLoanForRequest:: request does not have an itemId");
      return emptyAsync();
    }

    return findOpenLoanForItem(request.getItem());
  }

  /**
   *
   * @param item the item with ID to fetch the open loan for
   * @return  success with loan if one found,
   * success with null if the no open loan is found,
   * failure if more than one open loan for the item found
   */
  public CompletableFuture<Result<Loan>> findOpenLoanForItem(Item item) {
    log.debug("findOpenLoanForItem:: parameters item: {}", item);
    return findOpenLoans(item.getItemId())
      .thenApply(loansResult -> loansResult.next(loans -> {
        if (loans.getTotalRecords() == 0) {
          log.info("findOpenLoanForItem:: no open loans found for item {}", item.getItemId());
          return succeeded(null);
        }
        else if(loans.getTotalRecords() == 1) {
          final Optional<Loan> firstLoan = loans.getRecords().stream().findFirst();
          log.info("findOpenLoanForItem:: found open loan {} for item {}", firstLoan.get().getId(), item.getItemId());

          return firstLoan
            .map(loan -> Result.of(() -> loan.withItem(item)))
            .orElse(Result.of(() -> null));
        } else {
          log.info("findOpenLoanForItem:: more than one open loan found for item {}", item.getItemId());
          return failedDueToServerError(format(
            "More than one open loan for item %s", item.getItemId()));
        }
      }));
  }

  public CompletableFuture<Result<Loan>> getById(String id) {
    return fetchLoan(id)
      .thenComposeAsync(this::fetchItem)
      .thenCompose(CompletableFuture::completedFuture)
      .thenComposeAsync(this::fetchUser)
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  private CompletableFuture<Result<Loan>> fetchLoan(String id) {
    return FetchSingleRecord.<Loan>forRecord("loan")
      .using(loansStorageClient)
      .mapTo(Loan::from)
      .whenNotFound(failed(new RecordNotFoundFailure("loan", id)))
      .fetch(id);
  }

  private CompletableFuture<Result<Loan>> fetchItem(Result<Loan> result) {
    log.debug("fetchItem:: parameters result: {}", () -> resultAsString(result));
    return result.combineAfter(itemRepository::fetchFor, Loan::withItem);
  }

  private CompletableFuture<Result<Loan>> fetchUser(Result<Loan> result) {
    log.debug("fetchUser:: parameters result: {}", () -> resultAsString(result));
    return result.combineAfter(userRepository::getUser, Loan::withUser);
  }

  public CompletableFuture<Result<Loan>> fetchLatestPatronInfoAddedComment(Loan loan) {
    log.debug("fetchLatestPatronInfoAddedComment:: parameters loan: {}", () ->loan);
    if (LoanAction.PATRON_INFO_ADDED.getValue().equals(loan.getAction())) {
      // If latest action is patron info then we don't need to check history
      log.debug("fetchLatestPatronInfoAddedComment:: the latest action is patron info");
      return completedFuture(Result.succeeded(loan.withLatestPatronInfoAddedComment(loan.getActionComment())));
    }
    return loanHistoryRepository.getLatestPatronInfoAdded(loan)
      .thenApply(r -> r.map(records -> loan.withLatestPatronInfoAddedComment(
        mapToLatestPatronInfoAddedComment(records))));
  }

  private String mapToLatestPatronInfoAddedComment(MultipleRecords<LoanHistory> loanHistoryRecords) {
    LoanHistory loanHistory = loanHistoryRecords.firstOrNull();
    String latestPatronInfoAddedComment = null;
    if (loanHistory != null) {
      latestPatronInfoAddedComment = loanHistory.getLoan().getActionComment();
      log.debug("mapToLatestPatronInfoAddedComment:: loan history contains patron info: {}", latestPatronInfoAddedComment);
    }
    return latestPatronInfoAddedComment;
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findLoansToAnonymize(
    PageLimit pageLimit) {

    log.debug("findLoansToAnonymize:: parameters pageLimit: {}", pageLimit);

    Result<CqlQuery> cqlQuery = getStatusCQLQuery("Closed")
      .combine(CqlQuery.hasValue(USER_ID), CqlQuery::and);

    return queryLoanStorage(cqlQuery, pageLimit);
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> queryLoanStorage(
    Result<CqlQuery> statusQuery, PageLimit pageLimit) {

    return statusQuery
        .after(q -> loansStorageClient.getMany(q, pageLimit))
        .thenApply(result -> result.next(this::mapResponseToLoans));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findClosedLoans(
    String userId, PageLimit pageLimit) {

    log.debug("findClosedLoans:: parameters userId: {}, pageLimit: {}", userId, pageLimit);

    Result<CqlQuery> query = exactMatch(USER_ID, userId);
    final Result<CqlQuery> statusQuery = getStatusCQLQuery("Closed");

   return queryLoanStorage(statusQuery.combine(query, CqlQuery::and), pageLimit);
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findBy(String query) {
    return loansStorageClient.getManyWithRawQueryStringParameters(query)
      .thenApply(flatMapResult(this::mapResponseToLoans))
      .thenComposeAsync(loans -> itemRepository.fetchItemsFor(loans, Loan::withItem));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findByIds(Collection<String> loanIds) {
    log.debug("findByIds:: parameters loanIds: {}", () -> collectionAsString(loanIds));
    FindWithMultipleCqlIndexValues<Loan> fetcher =
      findWithMultipleCqlIndexValues(loansStorageClient, RECORDS_PROPERTY_NAME, Loan::from);

    return fetcher.findByIds(loanIds)
      .thenComposeAsync(loans -> itemRepository.fetchItemsFor(loans, Loan::withItem));
  }

  public CompletableFuture<Result<Collection<Loan>>> findByItemIds(
    Collection<String> itemIds) {

    log.debug("findByItemIds:: parameters itemIds: {}", () -> collectionAsString(itemIds));

    Result<CqlQuery> statusQuery = exactMatch(ITEM_STATUS, IN_TRANSIT.getValue());
    FindWithMultipleCqlIndexValues<Loan> fetcher = findWithMultipleCqlIndexValues(
      loansStorageClient, RECORDS_PROPERTY_NAME, Loan::from);

    return fetcher.findByIdIndexAndQuery(itemIds, ITEM_ID, statusQuery)
      .thenApply(mapResult(MultipleRecords::getRecords));
  }

  private Result<MultipleRecords<Loan>> mapResponseToLoans(Response response) {
    log.debug("mapResponseToLoans:: parameters response: {}", response);
    return MultipleRecords.from(response, Loan::from, RECORDS_PROPERTY_NAME);
  }

  private static void addIsDcbProperty(Loan loan, Item item, JsonObject storageLoan) {
    write(storageLoan, IS_DCB, isDcbLoan(loan, item));
  }

  private static boolean isDcbLoan(Loan loan, Item item) {
      return item.isDcbItem() || (nonNull(loan.getUser()) && nonNull(loan.getUser().getLastName())
              && loan.getUser().getLastName().equalsIgnoreCase(DCB_USER_LASTNAME));
  }

  private static JsonObject mapToStorageRepresentation(Loan loan, Item item) {
    log.debug("mapToStorageRepresentation:: parameters loan: {}, item: {}", loan, item);
    JsonObject storageLoan = loan.asJson();

    keepPatronGroupIdAtCheckoutProperties(loan, storageLoan);
    removeProperty(storageLoan, "metadata");
    removeSummaryProperties(storageLoan);
    keepLatestItemStatus(item, storageLoan);
    removeProperty(storageLoan, BORROWER);
    removeProperty(storageLoan, LOAN_POLICY);
    removeProperty(storageLoan, FEESANDFINES);
    removeProperty(storageLoan, OVERDUE_FINE_POLICY);
    removeProperty(storageLoan, LOST_ITEM_POLICY);
    addIsDcbProperty(loan, item, storageLoan);
    updatePolicy(storageLoan, loan.getLoanPolicy(), "loanPolicyId");
    updatePolicy(storageLoan, loan.getOverdueFinePolicy(), "overdueFinePolicyId");
    updatePolicy(storageLoan, loan.getLostItemPolicy(), "lostItemPolicyId");

    return storageLoan;
  }

  private static void removeProperty(JsonObject storageLoan, String propertyName) {
    storageLoan.remove(propertyName);
  }

  private static void keepLatestItemStatus(Item item, JsonObject storageLoan) {
    storageLoan.remove(ITEM_STATUS);
    storageLoan.put(ITEM_STATUS, item.getStatus().getValue());
  }

  private static void updatePolicy(JsonObject storageLoan, Policy policy,
    String policyName) {

    if (nonNull(policy) && policy.getId() != null) {
      storageLoan.put(policyName, policy.getId());
    }
  }

  private static void removeSummaryProperties(JsonObject storageLoan) {
    storageLoan.remove(BORROWER);
    storageLoan.remove("item");
    storageLoan.remove("checkinServicePoint");
    storageLoan.remove("checkoutServicePoint");
    storageLoan.remove(PATRON_GROUP_AT_CHECKOUT);
  }

 private static void keepPatronGroupIdAtCheckoutProperties(Loan loan, JsonObject storageLoan) {
    if (nonNull(loan.getUser()) && nonNull(loan.getUser().getPatronGroup())) {
      write(storageLoan, PATRON_GROUP_ID_AT_CHECKOUT,
        loan.getUser().getPatronGroup().getId());
    }

    if (storageLoan.containsKey(PATRON_GROUP_AT_CHECKOUT)) {
      write(storageLoan, PATRON_GROUP_ID_AT_CHECKOUT,
        storageLoan.getJsonObject(PATRON_GROUP_AT_CHECKOUT).getString("id"));
    }
 }

  public CompletableFuture<Result<Boolean>> hasOpenLoan(String itemId) {
    log.debug("hasOpenLoan:: parameters itemId: {}", itemId);
    return findOpenLoans(itemId)
      .thenApply(r -> r.map(loans -> !loans.getRecords().isEmpty()));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findOpenLoans(Item item) {
    log.debug("findOpenLoans:: parameters item: {}", item);
    return findOpenLoans(item.getItemId());
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> findOpenLoans(String itemId) {
    log.debug("findOpenLoans:: parameters itemId: {}", itemId);
    final Result<CqlQuery> statusQuery = getStatusCQLQuery("Open");
    final Result<CqlQuery> itemIdQuery = exactMatch(ITEM_ID, itemId);

    return queryLoanStorage(statusQuery.combine(itemIdQuery, CqlQuery::and), one());
  }

  public CompletableFuture<Result<MultipleRecords<Request>>> findOpenLoansFor(
    MultipleRecords<Request> multipleRequests) {

    log.debug("findOpenLoansFor:: parameters multipleRequests: {}",
      () -> multipleRecordsAsString(multipleRequests));

    Collection<Request> requests = multipleRequests.getRecords();

    //TODO: Extract this repeated getting of a collection of property values
    final List<String> itemsToFetchLoansFor = requests.stream()
      .filter(Objects::nonNull)
      .map(Request::getItemId)
      .filter(Objects::nonNull)
      .distinct()
      .collect(Collectors.toList());

    if(itemsToFetchLoansFor.isEmpty()) {
      log.info("No items to search for current loans for");
      return completedFuture(succeeded(multipleRequests));
    }

    final FindWithMultipleCqlIndexValues<Loan> fetcher =
      findWithMultipleCqlIndexValues(loansStorageClient, RECORDS_PROPERTY_NAME, Loan::from);

    return fetcher.findByIdIndexAndQuery(itemsToFetchLoansFor, ITEM_ID, getStatusCQLQuery("Open"))
      .thenApply(multipleLoansResult -> multipleLoansResult.next(
        loans -> matchLoansToRequests(multipleRequests, loans)));
  }

  private Result<CqlQuery> getStatusCQLQuery(String status) {
    return exactMatch("status.name", status);
  }

  private Result<MultipleRecords<Request>> matchLoansToRequests(
    MultipleRecords<Request> requests, MultipleRecords<Loan> loans) {

    log.debug("matchLoansToRequests:: parameters requests: {}, loans: {}",
      () -> multipleRecordsAsString(requests), () -> multipleRecordsAsString(loans));
    return of(() ->
      requests.mapRecords(request -> matchLoansToRequest(request, loans)));
  }

  private Request matchLoansToRequest(Request request,
    MultipleRecords<Loan> loans) {

    log.debug("matchLoansToRequest:: parameters request: {}, loans: {}",
      () -> request, () -> multipleRecordsAsString(loans));
    final Map<String, Loan> loanMap = loans.toMap(Loan::getItemId);

    return request
      .withLoan(loanMap.getOrDefault(request.getItemId(), null));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findOpenLoansByUserIdWithItem(
    PageLimit loansLimit, LoanAndRelatedRecords loanAndRelatedRecords) {

    log.debug("findOpenLoansByUserIdWithItem:: parameters loansLimit: {}, loanAndRelatedRecords: {}",
      loansLimit, loanAndRelatedRecords);

    return findOpenLoansByUserIdWithItem(loansLimit,
      loanAndRelatedRecords.getLoan().getUser().getId());
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findOpenLoansByUserIdWithItem(
    PageLimit loansLimit, String userId) {

    log.debug("findOpenLoansByUserIdWithItem:: parameters loansLimit: {}, userId: {}",
      loansLimit, userId);

    return findOpenLoansByUserId(loansLimit, userId)
      .thenComposeAsync(loans -> itemRepository.fetchItemsFor(loans, Loan::withItem));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findOpenLoansByUserIdWithItemAndHoldings(
    PageLimit loansLimit, String userId) {

    log.debug("findOpenLoansByUserIdWithItemAndHoldings:: parameters loansLimit: {}, userId: {}",
      loansLimit, userId);

    // Only fetching HoldingsRecord for each item to avoid fetching instances, locations etc.
    return findOpenLoansByUserId(loansLimit, userId)
      .thenComposeAsync(loans -> itemRepository.fetchItemsWithHoldings(loans, Loan::withItem));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findOpenLoansByUserId(
    PageLimit loansLimit, String userId) {

    log.debug("findOpenLoansByUserId:: parameters loansLimit: {}, userId: {}",
      loansLimit, userId);

    final Result<CqlQuery> statusQuery = getStatusCQLQuery("Open");
    final Result<CqlQuery> userIdQuery = exactMatch(USER_ID, userId);

    Result<CqlQuery> cqlQueryResult = statusQuery
      .combine(userIdQuery, CqlQuery::and);

    return queryLoanStorage(cqlQueryResult, loansLimit);
  }

  public CompletableFuture<Result<Loan>> findLastLoanForItem(String itemId) {
    log.debug("findLastLoanForItem:: parameters itemId: {}", itemId);
    final Result<CqlQuery> cqlQuery = exactMatch(ITEM_ID, itemId)
      .map(cql -> cql.sortBy(descending(LOAN_DATE)));

    return queryLoanStorage(cqlQuery, one())
      .thenApply(mapResult(MultipleRecords::firstOrNull));
  }

  public CompletableFuture<Result<Loan>> findLoanWithClosestDueDate(List<String> itemIds,
    List<String> loanIds) {

    log.debug("findLoanWithClosestDueDate:: parameters itemIds: {}, loanIds: {}",
      () -> collectionAsString(itemIds), () -> collectionAsString(loanIds));
    if (itemIds == null || itemIds.isEmpty()) {
      log.info("findLoanWithClosestDueDate: itemIds are null or empty");
      return ofAsync(() -> null);
    }

    Result<CqlQuery> cqlQuery = exactMatchAny(ITEM_ID, itemIds)
      .combine(getStatusCQLQuery("Open"), CqlQuery::and);
    if (!loanIds.isEmpty()) {
      cqlQuery = cqlQuery.combine(notIn(ID, loanIds), CqlQuery::and);
    }

    return queryLoanStorage(cqlQuery.map(query -> query.sortBy(ascending(DUE_DATE))), one())
      .thenApply(mapResult(MultipleRecords::firstOrNull));
  }

  public CompletableFuture<Result<Loan>> findLoanForAccount(Account account) {
    log.debug("findLoanForAccount:: parameters account: {}", account);
    if (account == null) {
      log.info("findLoanForAccount: account is null");
      return completedFuture(succeeded(null));
    }

    return getById(account.getLoanId());
  }

  @Override
  public CompletableFuture<Result<MultipleRecords<Loan>>> getMany(CqlQuery cqlQuery,
    PageLimit pageLimit, Offset offset) {

    log.debug("getMany:: parameters cqlQuery: {}, pageLimit: {}, offset: {}", cqlQuery, pageLimit, offset);

    return loansStorageClient.getMany(cqlQuery, pageLimit, offset)
      .thenApply(flatMapResult(this::mapResponseToLoans));
  }
}
