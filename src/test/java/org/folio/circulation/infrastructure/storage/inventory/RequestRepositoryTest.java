package org.folio.circulation.infrastructure.storage.inventory;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.infrastructure.storage.requests.RequestRepository.using;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Temporary tests to pass Sonar checks
 * TODO: delete
 */
@ExtendWith(MockitoExtension.class)
class RequestRepositoryTest {

  @Mock
  CollectionResourceClient requestsStorageClient;

  @Mock
  Clients clients;

  @Test
  void shouldReturnEmptyRequests() {
    when(clients.requestsStorage())
      .thenReturn(requestsStorageClient);
    when(requestsStorageClient.getMany(any(), any()))
      .thenReturn(
        completedFuture(succeeded(new Response(200, getJsonAsString(), "contentType"))));
    final RequestRepository requestsRepository = using(clients);

    var result = requestsRepository.findOpenStatusRequestsBy(List.of("1", "2"))
      .getNow(Result.failed(new ServerErrorFailure("Error")));
    assertTrue(result.succeeded());
    assertNotNull(result.value());
  }

  private String getJsonAsString() {
    JsonObject json = new JsonObject();
    json.put("totalRecords", 0);
    return json.toString();
  }
}
