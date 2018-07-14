package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;

public class RequestRepository {
  private final CollectionResourceClient requestsStorageClient;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;

  private RequestRepository(
    CollectionResourceClient requestsStorageClient,
    ItemRepository itemRepository,
    UserRepository userRepository) {

    this.requestsStorageClient = requestsStorageClient;
    this.itemRepository = itemRepository;
    this.userRepository = userRepository;
  }

  public static RequestRepository using(Clients clients) {
    return new RequestRepository(clients.requestsStorage(),
      new ItemRepository(clients, true, false),
      new UserRepository(clients));
  }

  public CompletableFuture<HttpResult<MultipleRecords<Request>>> findBy(String query) {
    return requestsStorageClient.getMany(query)
      .thenApply(this::mapResponseToRequests)
      .thenComposeAsync(requests ->
        itemRepository.fetchItemsFor(requests, Request::withItem));
  }

  private HttpResult<MultipleRecords<Request>> mapResponseToRequests(Response response) {
    return MultipleRecords.from(response, Request::from, "requests");
  }

  public CompletableFuture<HttpResult<Request>> getById(String id) {
    return fetchRequest(id)
      .thenComposeAsync(result -> result.combineAfter(itemRepository::fetchFor,
        Request::withItem))
      .thenComposeAsync(this::fetchRequester)
      .thenComposeAsync(this::fetchProxy);
  }

  private CompletableFuture<HttpResult<Request>> fetchRequest(String id) {
    return new SingleRecordFetcher<>(requestsStorageClient, "request", Request::from)
      .fetchSingleRecord(id);
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> update(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    CompletableFuture<HttpResult<RequestAndRelatedRecords>> requestUpdated =
      new CompletableFuture<>();

    final Request request = requestAndRelatedRecords.getRequest();

    final JsonObject representation = new RequestRepresentation()
      .storedRequest(request);

    requestsStorageClient.put(request.getId(), representation, response -> {
      if(response.getStatusCode() == 204) {
        requestUpdated.complete(succeeded(requestAndRelatedRecords));
      }
      else {
        requestUpdated.complete(failed(new ForwardOnFailure(response)));
      }
    });

    return requestUpdated;
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> create(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    CompletableFuture<HttpResult<RequestAndRelatedRecords>> onCreated = new CompletableFuture<>();

    final Request request = requestAndRelatedRecords.getRequest();

    JsonObject representation = new RequestRepresentation()
      .storedRequest(request);

    requestsStorageClient.post(representation, response -> {
      if (response.getStatusCode() == 201) {
        onCreated.complete(succeeded(
          requestAndRelatedRecords.withRequest(Request.from(response.getJson()))));
      } else {
        onCreated.complete(failed(new ForwardOnFailure(response)));
      }
    });

    return onCreated;
  }

  //TODO: Check if need to request requester
  private CompletableFuture<HttpResult<Request>> fetchRequester(HttpResult<Request> result) {
    return result.combineAfter(request ->
        userRepository.getUser(request.getUserId(), false),
        (request, requester) ->
          Request.from(request.asJson(), request.getItem(), requester, null));
  }

  //TODO: Check if need to request proxy
  private CompletableFuture<HttpResult<Request>> fetchProxy(HttpResult<Request> result) {
    return result.combineAfter(request ->
        userRepository.getUser(request.getProxyUserId(), false),
      (request, proxy) ->
        Request.from(request.asJson(), request.getItem(), request.getRequester(), proxy));
  }
}
