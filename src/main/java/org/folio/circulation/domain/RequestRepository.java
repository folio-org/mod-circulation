package org.folio.circulation.domain;

import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;

import java.util.concurrent.CompletableFuture;

public class RequestRepository {
  private final CollectionResourceClient requestsStorageClient;
  private final ItemRepository itemRepository;

  private RequestRepository(
    CollectionResourceClient requestsStorageClient,
    ItemRepository itemRepository) {

    this.requestsStorageClient = requestsStorageClient;
    this.itemRepository = itemRepository;
  }

  public static RequestRepository using(Clients clients) {
    return new RequestRepository(clients.requestsStorage(),
      new ItemRepository(clients, true, false));
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
      .thenComposeAsync(this::fetchItem);
  }

  private CompletableFuture<HttpResult<Request>> fetchRequest(String id) {
    return requestsStorageClient.get(id)
      .thenApply(response -> response.getStatusCode() == 200
        ? HttpResult.succeeded(Request.from(response.getJson()))
        : HttpResult.failed(new ForwardOnFailure(response)));
  }

  private CompletableFuture<HttpResult<Request>> fetchItem(HttpResult<Request> result) {
    return result.after(request ->
      itemRepository.fetchFor(request)
        .thenApply(itemResult -> itemResult.map(request::withItem)));
  }
}
