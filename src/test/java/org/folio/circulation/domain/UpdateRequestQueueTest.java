package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.reorder.ReorderQueueRequest;
import org.folio.circulation.domain.reorder.ReorderRequest;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.context.ReorderRequestContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

class UpdateRequestQueueTest {
  private final Response serverErrorBatchResponse =
    new Response(500, "Server Error", "text/plain");

  private UpdateRequestQueue updateRequestQueue;
  private RequestQueueRepository requestQueueRepository;
  private RequestRepository requestRepository;

  @BeforeEach
  public void setUp() {
    Clients clients = createServerErrorMockBatchRequestClient();

    final ItemRepository itemRepository = new ItemRepository(clients);
    final UserRepository userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients,
      itemRepository, userRepository);
    requestRepository = mock(RequestRepository.class);
    requestQueueRepository = spy(new RequestQueueRepository(
      RequestRepository.using(clients, itemRepository, userRepository, loanRepository)));

    updateRequestQueue =
      new UpdateRequestQueue(requestQueueRepository, requestRepository, null, null, null, null);
  }

  @Test
  void reorderShouldFailWhenBatchUpdateFails() throws Exception {
    ReorderRequestContext reorderContext = createReorderContext();

    CompletableFuture<Result<ReorderRequestContext>> completableFutureResult =
      updateRequestQueue.onReorder(succeeded(reorderContext));

    assertFailedOnFailureResponse(completableFutureResult);
  }

  @Test
  void removalShouldFailWhenBatchUpdateFails() throws Exception {
    UUID itemId = UUID.randomUUID();
    RequestQueue requestQueue = createRequestQueue(itemId, 4);
    Request requestToRemove = requestQueue.getRequests().iterator().next();

    when(requestQueueRepository.getByItemId(itemId.toString()))
      .thenReturn(completedFuture(succeeded(requestQueue)));

    CompletableFuture<Result<Request>> completableFutureResult =
      updateRequestQueue.onDeletion(requestToRemove);

    assertFailedOnFailureResponse(completableFutureResult);
  }

  @Test
  void moveToShouldFailWhenBatchUpdateFails() throws Exception {
    RequestAndRelatedRecords moveToRequestContext = createMoveRequestContext();

    CompletableFuture<Result<RequestAndRelatedRecords>> completableFutureResult =
      updateRequestQueue.onMovedTo(moveToRequestContext);

    assertFailedOnFailureResponse(completableFutureResult);
  }

  @Test
  void moveFromShouldFailWhenBatchUpdateFails() throws Exception {
    RequestAndRelatedRecords moveFromRequestContext = createMoveRequestContext();

    CompletableFuture<Result<RequestAndRelatedRecords>> completableFutureResult =
      updateRequestQueue.onMovedFrom(moveFromRequestContext);

    assertFailedOnFailureResponse(completableFutureResult);
  }

  @Test
  void cancellationShouldFailWhenBatchUpdateFails() throws Exception {
    RequestAndRelatedRecords cancellationContext = createCancellationContext();

    CompletableFuture<Result<RequestAndRelatedRecords>> completableFutureResult =
      updateRequestQueue.onCancellation(cancellationContext);

    assertFailedOnFailureResponse(completableFutureResult);
  }

  @Test
  void createShouldFailWhenBatchUpdateFails() throws Exception {
    RequestAndRelatedRecords createRequestContext = createRequestContext();

    CompletableFuture<Result<RequestAndRelatedRecords>> completableFutureResult =
      updateRequestQueue.onCreate(createRequestContext);

    assertFailedOnFailureResponse(completableFutureResult);
  }

  @Test
  void checkOutShouldFailWhenBatchUpdateFails() throws Exception {
    UUID itemId = UUID.randomUUID();
    LoanAndRelatedRecords checkOutContext = new LoanAndRelatedRecords(
        createLoan(itemId))
      .withRequestQueue(createRequestQueue(itemId, 3));

    Request fulfillRequest = checkOutContext.getRequestQueue()
      .getHighestPriorityFulfillableRequest();

    when(requestRepository.update(fulfillRequest))
      .thenReturn(completedFuture(succeeded(fulfillRequest)));

    CompletableFuture<Result<LoanAndRelatedRecords>> completableFutureResult =
      updateRequestQueue.onCheckOut(checkOutContext);

    assertFailedOnFailureResponse(completableFutureResult);
  }

  @Test
  void shouldDoNothingOnMovedFromWhenSourceItemIdDoNotMatchWithRequestItemId()
    throws Exception {

    String newOriginalItemId = UUID.randomUUID().toString();
    String newDestinationItemId = UUID.randomUUID().toString();

    RequestAndRelatedRecords moveFromRequestContext = createMoveRequestContext()
      .asMove(newOriginalItemId, newDestinationItemId);

    CompletableFuture<Result<RequestAndRelatedRecords>> completableFuture =
      updateRequestQueue.onMovedFrom(moveFromRequestContext);

    Result<RequestAndRelatedRecords> result = completableFuture
      .get(5, TimeUnit.SECONDS);

    assertTrue(result.succeeded());
    verifyNoInteractions(requestQueueRepository);
  }

  @Test
  void shouldDoNothingOnMovedToWhenSourceItemIdDoNotMatchWithRequestItemId()
    throws Exception {

    String newOriginalItemId = UUID.randomUUID().toString();
    String newDestinationItemId = UUID.randomUUID().toString();

    RequestAndRelatedRecords moveFromRequestContext = createMoveRequestContext()
      .asMove(newOriginalItemId, newDestinationItemId);

    CompletableFuture<Result<RequestAndRelatedRecords>> completableFuture =
      updateRequestQueue.onMovedTo(moveFromRequestContext);

    Result<RequestAndRelatedRecords> result = completableFuture
      .get(5, TimeUnit.SECONDS);

    assertTrue(result.succeeded());
    verifyNoInteractions(requestQueueRepository);
  }

  private Request requestAtPosition(UUID itemId, Integer position) {
    return Request.from(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .hold()
      .withItemId(itemId)
      .withPosition(position)
      .fulfilToHoldShelf()
      .create());
  }

  private Clients createServerErrorMockBatchRequestClient() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient requestBatchClient = mock(CollectionResourceClient.class);

    when(clients.requestsBatchStorage()).thenReturn(requestBatchClient);
    when(requestBatchClient.post(any(JsonObject.class)))
      .thenAnswer(rq -> completedFuture(of(() -> serverErrorBatchResponse)));

    return clients;
  }

  private ReorderRequestContext createReorderContext() {
    UUID itemId = UUID.randomUUID();
    RequestQueue requestQueue = createRequestQueue(itemId, 4);

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

    return new ReorderRequestContext(null, itemId.toString(), reorderQueueRequest)
      .withRequestQueue(requestQueue);
  }

  private RequestQueue createRequestQueue(UUID itemId, int numberOfRequests) {
    List<Request> allRequests = new ArrayList<>();

    for (int requestPosition = 1; requestPosition <= numberOfRequests; requestPosition++) {
      allRequests.add(requestAtPosition(itemId, requestPosition));
    }

    return new RequestQueue(allRequests);
  }

  private RequestAndRelatedRecords createMoveRequestContext() {
    UUID itemId = UUID.randomUUID();

    RequestQueue requestQueue = createRequestQueue(itemId, 4);
    Request request = requestQueue.getRequests().iterator().next();

    return new RequestAndRelatedRecords(request)
      .withRequestQueue(requestQueue)
      // setting destination == source, to handle both moveTo and moveFrom cases
      // it does not matter for tests
      .asMove(itemId.toString(), itemId.toString());
  }

  private RequestAndRelatedRecords createCancellationContext() {
    UUID itemId = UUID.randomUUID();

    RequestQueue requestQueue = createRequestQueue(itemId, 4);
    Request request = requestQueue.getRequests().iterator().next();
    request.changeStatus(RequestStatus.CLOSED_CANCELLED);

    requestQueue.remove(request);

    return new RequestAndRelatedRecords(request)
      .withRequestQueue(requestQueue);
  }

  private RequestAndRelatedRecords createRequestContext() {
    UUID itemId = UUID.randomUUID();

    return new RequestAndRelatedRecords(requestAtPosition(itemId, 0))
      .withRequestQueue(createRequestQueue(itemId, 3));
  }

  private <T> void assertFailedOnFailureResponse(
    CompletableFuture<Result<T>> completableFuture) throws Exception {

    Result<T> result = completableFuture.get(5, TimeUnit.SECONDS);

    assertTrue(result.failed());
    assertTrue(result.cause() instanceof ForwardOnFailure);

    ForwardOnFailure forwardResponse = (ForwardOnFailure) result.cause();
    assertThat(forwardResponse.getFailureResponse(), is(serverErrorBatchResponse));
  }

  private Loan createLoan(UUID itemId) {
    final JsonObject loanRepresentation = new JsonObject()
      .put("id", UUID.randomUUID().toString());
    var itemRepresentation = new JsonObject()
      .put("id", itemId.toString());
    var item = Item.from(itemRepresentation);

    return Loan.from(loanRepresentation).withItem(item);
  }
}
