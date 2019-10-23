package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.reorder.ReorderQueueRequest;
import org.folio.circulation.domain.reorder.ReorderRequest;
import org.folio.circulation.resources.context.ReorderRequestContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

public class UpdateRequestQueueTest {

  @Test
  public void testOnReorderWhenServerErrorOccurred() throws Exception {
    ReorderRequestContext context = createContext();
    Response batchResponse = new Response(500, "Server Error", "text/plain");

    Clients clients = failBatchRequestUpdateClients(batchResponse);
    RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);

    UpdateRequestQueue updateRequestQueue =
      new UpdateRequestQueue(requestQueueRepository, null, null);

    CompletableFuture<Result<ReorderRequestContext>> completableFutureResult =
      updateRequestQueue.onReorder(Result.succeeded(context));

    // Assert that result is failed with ForwardOnFailure
    Result<ReorderRequestContext> result = completableFutureResult
      .get(5, TimeUnit.SECONDS);

    assertTrue(result.failed());
    assertTrue(result.cause() instanceof ForwardOnFailure);

    ForwardOnFailure forwardResponse = (ForwardOnFailure) result.cause();
    assertThat(forwardResponse.getFailureResponse(), is(batchResponse));
  }

  private Request requestAtPosition(UUID itemId, Integer position) {
    return Request.from(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .hold()
      .withItemId(itemId)
      .withPosition(position)
      .create());
  }

  private Clients failBatchRequestUpdateClients(Response responseToReturnOnBatch) {
    Clients clients = mock(Clients.class);
    CollectionResourceClient requestBatchClient = mock(CollectionResourceClient.class);

    when(clients.requestsBatchStorage()).thenReturn(requestBatchClient);
    when(requestBatchClient.post(any(JsonObject.class)))
      .thenAnswer(rq -> completedFuture(responseToReturnOnBatch));

    return clients;
  }

  private ReorderRequestContext createContext() {
    UUID itemId = UUID.randomUUID();

    final Request firstRequest = requestAtPosition(itemId, 1);
    final Request secondRequest = requestAtPosition(itemId, 2);
    final Request thirdRequest = requestAtPosition(itemId, 3);
    final Request fourthRequest = requestAtPosition(itemId, 4);

    RequestQueue requestQueue = new RequestQueue(Arrays
      .asList(firstRequest, secondRequest, thirdRequest, fourthRequest));

    ReorderQueueRequest reorderQueueRequest = new ReorderQueueRequest();
    reorderQueueRequest.setReorderedQueue(new ArrayList<>());

    final int requestCount = requestQueue.getRequests().size();
    for (Request request : requestQueue.getRequests()) {
      ReorderRequest reorderRequest = new ReorderRequest();
      reorderQueueRequest.getReorderedQueue().add(reorderRequest);

      reorderRequest.setId(request.getId());
      // i.e. reverse positions
      reorderRequest.setNewPosition(requestCount - request.getPosition());
    }

    return new ReorderRequestContext(itemId.toString(), reorderQueueRequest)
      .withRequestQueue(requestQueue);
  }
}
