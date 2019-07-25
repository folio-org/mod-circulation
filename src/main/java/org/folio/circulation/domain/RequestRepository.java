package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.notice.schedule.RequestScheduledNoticeService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;

import io.vertx.core.json.JsonObject;

public class RequestRepository {
  private final CollectionResourceClient requestsStorageClient;
  private final CollectionResourceClient cancellationReasonStorageClient;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final LoanRepository loanRepository;
  private final ServicePointRepository servicePointRepository;
  private final PatronGroupRepository patronGroupRepository;
  private final RequestScheduledNoticeService requestScheduledNoticeService;

  private RequestRepository(
    CollectionResourceClient requestsStorageClient,
    CollectionResourceClient cancellationReasonStorageClient,
    ItemRepository itemRepository,
    UserRepository userRepository,
    LoanRepository loanRepository,
    ServicePointRepository servicePointRepository,
    PatronGroupRepository patronGroupRepository,
    RequestScheduledNoticeService requestScheduledNoticeService) {

    this.requestsStorageClient = requestsStorageClient;
    this.cancellationReasonStorageClient = cancellationReasonStorageClient;
    this.itemRepository = itemRepository;
    this.userRepository = userRepository;
    this.loanRepository = loanRepository;
    this.servicePointRepository = servicePointRepository;
    this.patronGroupRepository = patronGroupRepository;
    this.requestScheduledNoticeService = requestScheduledNoticeService;
  }

  public static RequestRepository using(Clients clients) {
    return using(clients, false);
  }

  public static RequestRepository using(Clients clients, boolean fetchMaterialType) {
    return new RequestRepository(
      clients.requestsStorage(),
      clients.cancellationReasonStorage(),
      new ItemRepository(clients, true, fetchMaterialType, true),
      new UserRepository(clients),
      new LoanRepository(clients),
      new ServicePointRepository(clients),
      new PatronGroupRepository(clients),
      RequestScheduledNoticeService.using(clients));
  }

  public CompletableFuture<Result<MultipleRecords<Request>>> findBy(String query) {
    return requestsStorageClient.getManyWithRawQueryStringParameters(query)
      .thenApply(this::mapResponseToRequests)
      .thenComposeAsync(result -> itemRepository.fetchItemsFor(result, Request::withItem))
      .thenComposeAsync(result -> result.after(loanRepository::findOpenLoansFor))
      .thenComposeAsync(result -> result.after(servicePointRepository::findServicePointsForRequests))
      .thenComposeAsync(result -> result.after(userRepository::findUsersForRequests))
      .thenComposeAsync(result -> result.after(patronGroupRepository::findPatronGroupsForRequestsUsers));
  }

  //TODO: try to consolidate this further with above
  CompletableFuture<Result<MultipleRecords<Request>>> findBy(
    CqlQuery query, Integer pageLimit) {

    return requestsStorageClient.getMany(query, pageLimit)
      .thenApply(result -> result.next(this::mapResponseToRequests))
      .thenComposeAsync(requests ->
        itemRepository.fetchItemsFor(requests, Request::withItem));
  }

  CompletableFuture<Result<MultipleRecords<Request>>> findByWithoutItems(
    CqlQuery query, Integer pageLimit) {

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

  //TODO: May need to fetch updated representation of request
  public CompletableFuture<Result<Request>> update(Request request) {
    final JsonObject representation
      = new StoredRequestRepresentation().storedRequest(request);

    final ResponseInterpreter<Request> interpreter = new ResponseInterpreter<Request>()
      .on(204, of(() -> request))
      .otherwise(forwardOnFailure());

    return requestsStorageClient.put(request.getId(), representation)
      .thenApply(interpreter::apply)
      .thenCompose(this::fetchRequesterIfNotPresented)
      .thenApply(r -> r.next(requestScheduledNoticeService::rescheduleRequestNotices));
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
      .flatMapOn(201, mapUsingJson(request::withRequestJsonRepresentation))
      .otherwise(forwardOnFailure());

    return requestsStorageClient.post(representation)
      .thenApply(interpreter::apply)
      .thenApply(mapResult(requestAndRelatedRecords::withRequest));
  }

  public CompletableFuture<Result<Request>> delete(Request request) {
    final ResponseInterpreter<Request> interpreter = new ResponseInterpreter<Request>()
      .on(204, of(() -> request))
      .otherwise(forwardOnFailure());

    return requestsStorageClient.delete(request.getId())
      .thenApply(interpreter::apply);
  }

  public CompletableFuture<Result<Request>> loadCancellationReason(Request request) {
    if(isNull(request) || isNull(request.getCancellationReasonId())) {
      return ofAsync(() -> null);
    }

    return FetchSingleRecord.<Request>forRecord("cancellation reason")
      .using(cancellationReasonStorageClient)
      .mapTo(request::withCancellationReasonJsonRepresentation)
      .whenNotFound(succeeded(request))
      .fetch(request.getCancellationReasonId());
  }

  //TODO: Check if need to request requester
  private CompletableFuture<Result<Request>> fetchRequester(Result<Request> result) {
    return result.combineAfter(request ->
      getUser(request.getUserId()), Request::withRequester);
  }

  private CompletableFuture<Result<Request>> fetchRequesterIfNotPresented(Result<Request> result) {
    return result.after(request -> request.getRequester() == null ?
      fetchRequester(result) : completedFuture(succeeded(request)));
  }

  //TODO: Check if need to request proxy
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
}
