package org.folio.circulation.infrastructure.storage.requests;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.StoredRequestRepresentation;
import org.folio.circulation.domain.User;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.storage.RequestBatch;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class RequestRepository {
  private final CollectionResourceClient requestsStorageClient;
  private final CollectionResourceClient requestsBatchStorageClient;
  private final CollectionResourceClient cancellationReasonStorageClient;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final LoanRepository loanRepository;
  private final ServicePointRepository servicePointRepository;
  private final PatronGroupRepository patronGroupRepository;

  private RequestRepository(Clients clients, ItemRepository itemRepository,
    UserRepository userRepository, LoanRepository loanRepository,
    ServicePointRepository servicePointRepository,
    PatronGroupRepository patronGroupRepository) {

    this.requestsStorageClient = clients.getRequestsStorageClient();
    this.requestsBatchStorageClient = clients.getRequestsBatchStorageClient();
    this.cancellationReasonStorageClient = clients.getCancellationReasonStorageClient();
    this.itemRepository = itemRepository;
    this.userRepository = userRepository;
    this.loanRepository = loanRepository;
    this.servicePointRepository = servicePointRepository;
    this.patronGroupRepository = patronGroupRepository;
  }

  public static RequestRepository using(org.folio.circulation.support.Clients clients) {
    return using(clients, false);
  }

  public static RequestRepository using(org.folio.circulation.support.Clients clients, boolean fetchMaterialType) {
    return new RequestRepository(
      new Clients(clients.requestsStorage(), clients.requestsBatchStorage(),
        clients.cancellationReasonStorage()),
      new ItemRepository(clients, true, fetchMaterialType, true),
      new UserRepository(clients),
      new LoanRepository(clients),
      new ServicePointRepository(clients),
      new PatronGroupRepository(clients));
  }

  public CompletableFuture<Result<MultipleRecords<Request>>> findBy(String query) {
    return requestsStorageClient.getManyWithRawQueryStringParameters(query)
      .thenApply(flatMapResult(this::mapResponseToRequests))
      .thenComposeAsync(result -> itemRepository.fetchItemsFor(result, Request::withItem))
      .thenComposeAsync(result -> result.after(loanRepository::findOpenLoansFor))
      .thenComposeAsync(result -> result.after(servicePointRepository::findServicePointsForRequests))
      .thenComposeAsync(result -> result.after(userRepository::findUsersForRequests))
      .thenComposeAsync(result -> result.after(patronGroupRepository::findPatronGroupsForRequestsUsers));
  }

  CompletableFuture<Result<MultipleRecords<Request>>> findBy(CqlQuery query,
    PageLimit pageLimit) {

    return findByWithoutItems(query, pageLimit)
      .thenComposeAsync(requests ->
        itemRepository.fetchItemsFor(requests, Request::withItem));
  }

  CompletableFuture<Result<MultipleRecords<Request>>> findByWithoutItems(
    CqlQuery query, PageLimit pageLimit) {

    return requestsStorageClient.getMany(query, pageLimit)
      .thenApply(result -> result.next(this::mapResponseToRequests));
  }

  private Result<MultipleRecords<Request>> mapResponseToRequests(Response response) {
    return MultipleRecords.from(response, Request::from, "requests");
  }

  public CompletableFuture<Result<Boolean>> exists(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return exists(requestAndRelatedRecords.getRequest());
  }

  private CompletableFuture<Result<Boolean>> exists(Request request) {
    return exists(request.getId());
  }

  private CompletableFuture<Result<Boolean>> exists(String id) {
    return createSingleRequestFetcher(new ResponseInterpreter<Boolean>()
      .on(200, of(() -> true))
      .on(404, of(() -> false)))
      .fetch(id);
  }

  public CompletableFuture<Result<Request>> getById(String id) {
    return fetchRequest(id)
      .thenComposeAsync(result -> result.combineAfter(itemRepository::fetchFor,
        Request::withItem))
      .thenComposeAsync(this::fetchRequester)
      .thenComposeAsync(this::fetchProxy)
      .thenComposeAsync(this::fetchLoan)
      .thenComposeAsync(this::fetchPickupServicePoint)
      .thenComposeAsync(this::fetchPatronGroups);
  }

  private CompletableFuture<Result<Request>> fetchRequest(String id) {
    return createSingleRequestFetcher(new ResponseInterpreter<Request>()
      .flatMapOn(200, mapUsingJson(Request::from))
      .on(404, failed(new RecordNotFoundFailure("request", id))))
      .fetch(id);
  }

  public CompletableFuture<Result<Request>> update(Request request) {
    final JsonObject representation
      = new StoredRequestRepresentation().storedRequest(request);

    final ResponseInterpreter<Request> interpreter = new ResponseInterpreter<Request>()
      .on(204, of(() -> request))
      .otherwise(forwardOnFailure());

    return requestsStorageClient.put(request.getId(), representation)
      .thenApply(interpreter::flatMap);
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> update(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return update(requestAndRelatedRecords.getRequest())
      .thenApply(r -> r.map(requestAndRelatedRecords::withRequest));
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> create(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    JsonObject representation = new StoredRequestRepresentation()
      .storedRequest(request);

    final ResponseInterpreter<Request> interpreter = new ResponseInterpreter<Request>()
      .flatMapOn(201, mapUsingJson(request::withRequestRepresentation))
      .otherwise(forwardOnFailure());

    return requestsStorageClient.post(representation)
      .thenApply(interpreter::flatMap)
      .thenApply(mapResult(requestAndRelatedRecords::withRequest));
  }

  public CompletableFuture<Result<Request>> delete(Request request) {
    final ResponseInterpreter<Request> interpreter = new ResponseInterpreter<Request>()
      .on(204, of(() -> request))
      .otherwise(forwardOnFailure());

    return requestsStorageClient.delete(request.getId())
      .thenApply(flatMapResult(interpreter::apply));
  }

  public CompletableFuture<Result<Request>> loadCancellationReason(Request request) {
    if(isNull(request) || isNull(request.getCancellationReasonId())) {
      return ofAsync(() -> null);
    }

    return FetchSingleRecord.<Request>forRecord("cancellation reason")
      .using(cancellationReasonStorageClient)
      .mapTo(request::withCancellationReasonRepresentation)
      .whenNotFound(succeeded(request))
      .fetch(request.getCancellationReasonId());
  }

  CompletableFuture<Result<Collection<Request>>> batchUpdate(
    Collection<Request> requests) {

    if (requests == null || requests.isEmpty()) {
      return CompletableFuture.completedFuture(succeeded(requests));
    }

    ResponseInterpreter<Collection<Request>> interpreter =
      new ResponseInterpreter<Collection<Request>>()
        .on(201, of(() -> requests))
        .otherwise(forwardOnFailure());

    RequestBatch requestBatch = new RequestBatch(requests);
    return requestsBatchStorageClient.post(requestBatch.toJson())
      .thenApply(interpreter::flatMap);
  }

  private CompletableFuture<Result<Request>> fetchRequester(Result<Request> result) {
    return result.combineAfter(request ->
      getUser(request.getUserId()), Request::withRequester);
  }

  private CompletableFuture<Result<Request>> fetchProxy(Result<Request> result) {
    return result.combineAfter(request ->
      getUser(request.getProxyUserId()), Request::withProxy);
  }

  private CompletableFuture<Result<Request>> fetchLoan(Result<Request> result) {
    return result.combineAfter(loanRepository::findOpenLoanForRequest, Request::withLoan);
  }

  private CompletableFuture<Result<Request>> fetchPickupServicePoint(Result<Request> result) {
    return result.combineAfter(request -> getServicePoint(request.getPickupServicePointId()),
        Request::withPickupServicePoint);
  }

  private CompletableFuture<Result<Request>> fetchPatronGroups(Result<Request> result) {
    return patronGroupRepository.findPatronGroupsForSingleRequestUsers(result);
  }

  private CompletableFuture<Result<User>> getUser(String userId) {
    return userRepository.getUser(userId);
  }

  private CompletableFuture<Result<ServicePoint>> getServicePoint(String servicePointId) {
    return servicePointRepository.getServicePointById(servicePointId);
  }

  private <R> SingleRecordFetcher<R> createSingleRequestFetcher(
    ResponseInterpreter<R> interpreter) {

    return new SingleRecordFetcher<>(requestsStorageClient, "request", interpreter);
  }

  @AllArgsConstructor
  @Getter
  private static class Clients {
    private final CollectionResourceClient requestsStorageClient;
    private final CollectionResourceClient requestsBatchStorageClient;
    private final CollectionResourceClient cancellationReasonStorageClient;
  }
}
