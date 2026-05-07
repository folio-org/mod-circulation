package org.folio.circulation.infrastructure.storage.requests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RequestRepositoryTest {

  private RequestRepository repository;
  private CollectionResourceClient requestsStorageClient;

  @BeforeEach
  void setUp() {
    Clients clients = mock(Clients.class);
    requestsStorageClient = mock(CollectionResourceClient.class);

    when(clients.requestsStorage()).thenReturn(requestsStorageClient);
    when(clients.requestsBatchStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.cancellationReasonStorage()).thenReturn(mock(CollectionResourceClient.class));

    repository = new RequestRepository(clients);
  }

  @Test
  void findRequestsToAnonymizeQueriesClosedRequests() {
    Response response = createMockResponse();
    when(requestsStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(response)));

    PageLimit pageLimit = PageLimit.limit(100);
    Result<MultipleRecords<Request>> result =
      repository.findRequestsToAnonymize(pageLimit).join();

    assertTrue(result.succeeded());
    verify(requestsStorageClient).getMany(any(CqlQuery.class), eq(pageLimit));
  }

  @Test
  void findClosedRequestsQueriesForSpecificUser() {
    Response response = createMockResponse();
    when(requestsStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(response)));

    String userId = "user-123";
    PageLimit pageLimit = PageLimit.limit(50);

    Result<MultipleRecords<Request>> result =
      repository.findClosedRequests(userId, pageLimit).join();

    assertTrue(result.succeeded());
    verify(requestsStorageClient).getMany(any(CqlQuery.class), eq(pageLimit));
  }

  @Test
  void findRequestsToAnonymizeReturnsEmptyWhenNoRequests() {
    Response response = createMockResponse();
    when(requestsStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(response)));

    Result<MultipleRecords<Request>> result =
      repository.findRequestsToAnonymize(PageLimit.limit(10)).join();

    assertTrue(result.succeeded());
    assertEquals(0, result.value().getTotalRecords());
  }

  @Test
  void findClosedRequestsReturnsEmptyWhenNoRequestsForUser() {
    Response response = createMockResponse();
    when(requestsStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(response)));

    Result<MultipleRecords<Request>> result =
      repository.findClosedRequests("user-456", PageLimit.limit(10)).join();

    assertTrue(result.succeeded());
    assertEquals(0, result.value().getTotalRecords());
  }

  private Response createMockResponse() {
    Response response = mock(Response.class);
    JsonObject body = new JsonObject()
      .put("requests", new JsonArray())
      .put("totalRecords", 0);
    when(response.getJson()).thenReturn(body);
    when(response.getStatusCode()).thenReturn(200);
    return response;
  }
}
