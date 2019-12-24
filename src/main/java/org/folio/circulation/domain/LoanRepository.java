package org.folio.circulation.domain;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.LoanProperties.PATRON_GROUP_AT_CHECKOUT;
import static org.folio.circulation.domain.representations.LoanProperties.PATRON_GROUP_ID_AT_CHECKOUT;
import static org.folio.circulation.resources.LoanCollectionResourceHelper.createItemNotFoundValidator;
import static org.folio.circulation.resources.LoanCollectionResourceHelper.getServicePointsForLoanAndRelated;
import static org.folio.circulation.support.CqlQuery.exactMatch;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.CommonResponseInterpreters.noContentRecordInterpreter;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import org.folio.circulation.domain.notice.schedule.DueDateScheduledNoticeService;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointLoanLocationValidator;
import org.folio.circulation.resources.LoanCollectionResourceHelper;
import org.folio.circulation.resources.LoanNoticeSender;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.CommonFailures;

import io.vertx.core.json.JsonObject;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoanRepository {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String ITEM_STATUS = "itemStatus";
  private static final String ITEM_ID = "itemId";

  private final CollectionResourceClient loansStorageClient;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final DueDateScheduledNoticeService scheduledNoticeService;
  private final Clients clients;

  public LoanRepository(Clients clients) {
    this.clients = clients;
    scheduledNoticeService = DueDateScheduledNoticeService.using(clients);
    loansStorageClient = clients.loansStorage();
    itemRepository = new ItemRepository(clients, true, true, true);
    userRepository = new UserRepository(clients);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> createLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    final Loan loan = loanAndRelatedRecords.getLoan();

    JsonObject storageLoan = mapToStorageRepresentation(loan, loan.getItem());

    final ResponseInterpreter<Loan> interpreter = new ResponseInterpreter<Loan>()
      .flatMapOn(201, mapUsingJson(loan::replaceRepresentation))
      .otherwise(forwardOnFailure());

    return loansStorageClient.post(storageLoan)
      .thenApply(interpreter::apply)
      .thenApply(mapResult(loanAndRelatedRecords::withLoan));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> updateLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return updateLoan(loanAndRelatedRecords.getLoan())
      .thenApply(mapResult(loanAndRelatedRecords::withLoan));
  }

  public CompletableFuture<Result<Loan>> updateLoan(Loan loan) {
    if(loan == null) {
      return completedFuture(of(() -> null));
    }

    JsonObject storageLoan = mapToStorageRepresentation(loan, loan.getItem());

    return loansStorageClient.put(loan.getId(), storageLoan)
      .thenApply(noContentRecordInterpreter(loan)::apply)
      .thenComposeAsync(r -> r.after(this::refreshLoanRepresentation));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> replaceLoan(Loan loan) {
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final UserRepository userRepository = new UserRepository(clients);

    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);
    final UpdateItem updateItem = new UpdateItem(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> singleValidationError("proxyUserId is not valid", "proxyUserId",
      loan.getProxyUserId()));

    final ItemNotFoundValidator itemNotFoundValidator = createItemNotFoundValidator(loan);

    final ServicePointLoanLocationValidator spLoanLocationValidator =
      new ServicePointLoanLocationValidator();


    final LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients);

    return completedFuture(succeeded(new LoanAndRelatedRecords(loan)))
      .thenCompose(larrResult ->
        getServicePointsForLoanAndRelated(larrResult, servicePointRepository))
      .thenApply(LoanCollectionResourceHelper::refuseWhenNotOpenOrClosed)
      .thenApply(LoanCollectionResourceHelper::refuseWhenOpenAndNoUserId)
      .thenApply(spLoanLocationValidator::checkServicePointLoanLocation)
      .thenApply(LoanCollectionResourceHelper::refuseWhenClosedAndNoCheckInServicePointId)
      .thenCombineAsync(itemRepository.fetchFor(loan), LoanCollectionResourceHelper::addItem)
      .thenCombineAsync(userRepository.getUser(loan.getUserId()), LoanCollectionResourceHelper::addUser)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueRepository.get(loan.getItemId()), LoanCollectionResourceHelper::addRequestQueue)
      .thenComposeAsync(result -> result.after(requestQueueUpdate::onCheckIn))
      .thenComposeAsync(result -> result.after(updateItem::onLoanUpdate))
      // Loan must be updated after item
      // due to snapshot of item status stored with the loan
      // as this is how the loan action history is populated
      .thenComposeAsync(result -> result.after(this::updateLoan))
      .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
      .thenCompose(r -> r.after(loanNoticeSender::sendManualDueDateChangeNotice));
  }

  /**
   *
   * @param request the request to fetch the open loan for the same item for
   * @return  success with loan if one found,
   * success with null if the no open loan is found,
   * failure if more than one open loan for the item found
   */
  public CompletableFuture<Result<Loan>> findOpenLoanForRequest(Request request) {
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
    return findOpenLoans(item.getItemId())
      .thenApply(loansResult -> loansResult.next(loans -> {
        //TODO: Consider introducing an unknown loan class, instead of null
        if (loans.getTotalRecords() == 0) {
          return succeeded(null);
        }
        else if(loans.getTotalRecords() == 1) {
          final Optional<Loan> firstLoan = loans.getRecords().stream().findFirst();

          return firstLoan
            .map(loan -> Result.of(() -> loan.withItem(item)))
            .orElse(Result.of(() -> null));
        } else {
          return failedDueToServerError(format(
            "More than one open loan for item %s", item.getItemId()));
        }
      }));
  }

  public CompletableFuture<Result<Loan>> getById(String id) {
    return fetchLoan(id)
      .thenComposeAsync(this::fetchItem)
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

  private CompletableFuture<Result<Loan>> refreshLoanRepresentation(Loan loan) {
    return new SingleRecordFetcher<>(loansStorageClient, "loan",
      new ResponseInterpreter<Loan>()
        .flatMapOn(200, mapUsingJson(loan::replaceRepresentation)))
      .fetch(loan.getId());
  }

  private CompletableFuture<Result<Loan>> fetchItem(Result<Loan> result) {
    return result.combineAfter(itemRepository::fetchFor, Loan::withItem);
  }

  //TODO: Check if user not found should result in failure?
  private CompletableFuture<Result<Loan>> fetchUser(Result<Loan> result) {
    return result.combineAfter(userRepository::getUser, Loan::withUser);
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findClosedLoans(int fetchLoansLimit) {
    return queryLoanStorage(fetchLoansLimit, getStatusCQLQuery("Closed"));
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> queryLoanStorage(
      int fetchLoansLimit, Result<CqlQuery> statusQuery) {

    return statusQuery
        .after(q -> loansStorageClient.getMany(q, fetchLoansLimit))
        .thenApply(result -> result.next(this::mapResponseToLoans));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findClosedLoans(
      String userId, int fetchLoansLimit) {
    Result<CqlQuery> query = exactMatch("userId", userId);
    final Result<CqlQuery> statusQuery = getStatusCQLQuery("Closed");
   return queryLoanStorage(fetchLoansLimit, statusQuery.combine(query, CqlQuery::and));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findBy(String query) {
    //TODO: Should fetch users for all loans
    return loansStorageClient.getManyWithRawQueryStringParameters(query)
      .thenApply(this::mapResponseToLoans)
      .thenComposeAsync(loans -> itemRepository.fetchItemsFor(loans, Loan::withItem));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findByIds(Collection<String> loanIds) {
    MultipleRecordFetcher<Loan> fetcher =
      new MultipleRecordFetcher<>(loansStorageClient, "loans", Loan::from);

    return fetcher.findByIds(loanIds)
      .thenComposeAsync(loans -> itemRepository.fetchItemsFor(loans, Loan::withItem));
  }

  private Result<MultipleRecords<Loan>> mapResponseToLoans(Response response) {
    return MultipleRecords.from(response, Loan::from, "loans");
  }

  private static JsonObject mapToStorageRepresentation(Loan loan, Item item) {
    JsonObject storageLoan = loan.asJson();

    keepPatronGroupIdAtCheckoutProperties(loan, storageLoan);
    removeChangeMetadata(storageLoan);
    removeSummaryProperties(storageLoan);
    keepLatestItemStatus(item, storageLoan);
    removeBorrowerProperties(storageLoan);
    removeLoanPolicyProperties(storageLoan);
    removeFeesAndFinesProperties(storageLoan);

    updateLastLoanPolicyUsedId(storageLoan, loan.getLoanPolicy());

    return storageLoan;
  }

  private static void removeLoanPolicyProperties(JsonObject storageLoan) {
    storageLoan.remove(LoanProperties.LOAN_POLICY);
  }

  private static void removeBorrowerProperties(JsonObject storageLoan) {
    storageLoan.remove(LoanProperties.BORROWER);
  }

  private static void keepLatestItemStatus(Item item, JsonObject storageLoan) {
    //TODO: Check for null item status
    storageLoan.remove(ITEM_STATUS);
    storageLoan.put(ITEM_STATUS, item.getStatus().getValue());
  }

  private static void updateLastLoanPolicyUsedId(JsonObject storageLoan,
                                                 LoanPolicy loanPolicy) {

    if(nonNull(loanPolicy) && loanPolicy.getId() != null) {
      storageLoan.put("loanPolicyId", loanPolicy.getId());
    }
  }

  private static void removeFeesAndFinesProperties(JsonObject storageLoan) {
    storageLoan.remove(LoanProperties.FEESANDFINES);
  }

  private static void removeChangeMetadata(JsonObject storageLoan) {
    storageLoan.remove("metadata");
  }

  private static void removeSummaryProperties(JsonObject storageLoan) {
    storageLoan.remove(LoanProperties.BORROWER);
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

    //TODO: Should this really be re-writing the value?
    if (storageLoan.containsKey(PATRON_GROUP_AT_CHECKOUT)) {
      write(storageLoan, PATRON_GROUP_ID_AT_CHECKOUT,
        storageLoan.getJsonObject(PATRON_GROUP_AT_CHECKOUT).getString("id"));
    }
 }

  public CompletableFuture<Result<Boolean>> hasOpenLoan(String itemId) {
    return findOpenLoans(itemId)
      .thenApply(r -> r.map(loans -> !loans.getRecords().isEmpty()));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findOpenLoans(Item item) {
    return findOpenLoans(item.getItemId());
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> findOpenLoans(String itemId) {
    final Result<CqlQuery> statusQuery = getStatusCQLQuery("Open");
    final Result<CqlQuery> itemIdQuery = exactMatch(ITEM_ID, itemId);

    return queryLoanStorage(1, statusQuery.combine(itemIdQuery, CqlQuery::and));
  }

  CompletableFuture<Result<MultipleRecords<Request>>> findOpenLoansFor(
    MultipleRecords<Request> multipleRequests) {

    //TODO: Need to handle multiple open loans for same item (with failure?)

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

    final Result<CqlQuery> statusQuery = getStatusCQLQuery("Open");
    final Result<CqlQuery> itemIdQuery = exactMatchAny(ITEM_ID, itemsToFetchLoansFor);

    return queryLoanStorage(requests.size(), statusQuery.combine(
        itemIdQuery, CqlQuery::and))
      .thenApply(multipleLoansResult -> multipleLoansResult.next(
        loans -> matchLoansToRequests(multipleRequests, loans)));
  }

  private Result<CqlQuery> getStatusCQLQuery(String status) {
    return exactMatch("status.name", status);
  }

  private Result<MultipleRecords<Request>> matchLoansToRequests(
    MultipleRecords<Request> requests,
    MultipleRecords<Loan> loans) {

    return of(() ->
      requests.mapRecords(request -> matchLoansToRequest(request, loans)));
  }

  private Request matchLoansToRequest(
    Request request,
    MultipleRecords<Loan> loans) {

    final Map<String, Loan> loanMap = loans.toMap(Loan::getItemId);

    return request
      .withLoan(loanMap.getOrDefault(request.getItemId(), null));
  }

}
