package org.folio.circulation.domain;

import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;

public class RequestRepository {
  private final CollectionResourceClient requestsStorageClient;

  private RequestRepository(CollectionResourceClient requestsStorageClient) {
    this.requestsStorageClient = requestsStorageClient;
  }

  public static RequestRepository using(Clients clients) {
    return new RequestRepository(clients.requestsStorage());
  }

  public CompletableFuture<HttpResult<MultipleRecords<Request>>> findBy(String query) {
    return requestsStorageClient.getMany(query)
      .thenApply(this::mapResponseToRequests);
  }

  private HttpResult<MultipleRecords<Request>> mapResponseToRequests(Response response) {
    if (response.getStatusCode() != 200) {
      return failed(new ServerErrorFailure(
        String.format("Failed to fetch loans from storage (%s:%s)",
          response.getStatusCode(), response.getBody())));
    }

    final MultipleRecordsWrapper wrappedRequests = MultipleRecordsWrapper.fromBody(
      response.getBody(), "requests");

    if (wrappedRequests.isEmpty()) {
      return succeeded(MultipleRecords.empty());
    }

    final MultipleRecords<Request> mapped = new MultipleRecords<>(
      wrappedRequests.getRecords()
        .stream()
        .map(Request::new)
        .collect(Collectors.toList()),
      wrappedRequests.getTotalRecords());

    return succeeded(mapped);
  }
}
