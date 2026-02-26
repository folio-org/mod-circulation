package org.folio.circulation.infrastructure.storage.requests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.anonymization.RequestAnonymizationRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class AnonymizeStorageRequestsRepositoryTest {

  @Test
  void shouldReturnSuccessWhenRequestIdsAreEmpty() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient client = mock(CollectionResourceClient.class);

    when(clients.requestsBatchStorage()).thenReturn(client);

    AnonymizeStorageRequestsRepository repository =
      new AnonymizeStorageRequestsRepository(clients);

    RequestAnonymizationRecords emptyRecords = new RequestAnonymizationRecords();

    Result<RequestAnonymizationRecords> result =
      repository.postAnonymizeStorageRequests(emptyRecords).join();

    assertTrue(result.succeeded());
    assertEquals(0, result.value().getAnonymizedRequestIds().size());
    verify(client, times(0)).post(any(JsonObject.class));
  }

  @Test
  void shouldPostToStorageWhenRequestIdsExist() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient client = mock(CollectionResourceClient.class);

    when(clients.requestsBatchStorage()).thenReturn(client);

    Response response = mock(Response.class);
    when(response.getStatusCode()).thenReturn(201);

    JsonObject responseBody = new JsonObject();

    when(response.getJson()).thenReturn(responseBody);
    when(client.post(any(JsonObject.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(response)));

    AnonymizeStorageRequestsRepository repository =
      new AnonymizeStorageRequestsRepository(clients);

    RequestAnonymizationRecords records = new RequestAnonymizationRecords()
      .withAnonymizedRequests(Arrays.asList("request-id-1", "request-id-2"));

    Result<RequestAnonymizationRecords> result =
      repository.postAnonymizeStorageRequests(records).join();

    assertTrue(result.succeeded());
    assertNotNull(result.value());
    verify(client, times(1)).post(any(JsonObject.class));
  }

  @Test
  void shouldCreateCorrectPayload() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient client = mock(CollectionResourceClient.class);

    when(clients.requestsBatchStorage()).thenReturn(client);

    Response response = mock(Response.class);
    when(response.getStatusCode()).thenReturn(201);
    when(response.getJson()).thenReturn(new JsonObject());

    when(client.post(any(JsonObject.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(response)));

    AnonymizeStorageRequestsRepository repository =
      new AnonymizeStorageRequestsRepository(clients);

    RequestAnonymizationRecords records = new RequestAnonymizationRecords()
      .withAnonymizedRequests(Collections.singletonList("request-1"));

    Result<RequestAnonymizationRecords> result =
      repository.postAnonymizeStorageRequests(records).join();

    assertTrue(result.succeeded());
    verify(client).post(any(JsonObject.class));
  }
}
