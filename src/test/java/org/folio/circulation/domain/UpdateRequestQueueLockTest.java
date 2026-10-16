package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.RequestQueueKey.QueueType.INSTANCE;
import static org.folio.circulation.domain.RequestQueueKey.QueueType.ITEM;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.folio.circulation.domain.configuration.RequestQueueLockConfiguration;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.services.RequestQueueLockService;
import org.folio.circulation.services.RequestQueueService;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class UpdateRequestQueueLockTest {
  private static final int TIMEOUT_SECONDS = 2;
  private static final TlrSettingsConfiguration TLR_ENABLED =
    new TlrSettingsConfiguration(true, false, false, null, null, null);

  @Mock
  private RequestQueueRepository requestQueueRepository;
  @Mock
  private RequestRepository requestRepository;
  @Mock
  private ServicePointRepository servicePointRepository;
  @Mock
  private SettingsRepository settingsRepository;
  @Mock
  private RequestQueueService requestQueueService;
  @Mock
  private CalendarRepository calendarRepository;
  @Mock
  private RequestQueueLockService lockService;

  private RequestQueueLockConfiguration lockConfiguration;
  private UpdateRequestQueue updateRequestQueue;

  @BeforeEach
  void setUp() {
    lockConfiguration = new RequestQueueLockConfiguration(true, 10_000, 1, 500);
    lenient().when(settingsRepository.lookupRequestQueueLockSettings())
      .thenReturn(completedFuture(succeeded(lockConfiguration)));
    lenient().when(lockService.execute(any(RequestQueueKey.class), eq(lockConfiguration), any()))
      .thenAnswer(invocation -> {
        Supplier<CompletableFuture<Result<Object>>> criticalSection = invocation.getArgument(2);
        return criticalSection.get();
      });

    updateRequestQueue = new UpdateRequestQueue(requestQueueRepository, requestRepository,
      servicePointRepository, settingsRepository, requestQueueService, calendarRepository,
      lockService);
  }

  @Test
  void checkOutLocksItemQueueAndPersistsFreshConsecutivePositions() throws Exception {
    UUID itemId = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();
    RequestQueue initialQueue = requestQueue(itemId, instanceId, 3);
    Request selectedRequest = initialQueue.getRequests().iterator().next();
    RequestQueue freshQueue = copy(initialQueue);
    LoanAndRelatedRecords records = records(itemId, instanceId, initialQueue,
      TlrSettingsConfiguration.defaultSettings());

    allowSuccessfulQueueUpdate(freshQueue);

    Result<LoanAndRelatedRecords> result = updateRequestQueue
      .onCheckOut(records, selectedRequest)
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.succeeded(), is(true));
    verify(lockService).execute(eq(new RequestQueueKey(ITEM, itemId.toString())),
      eq(lockConfiguration), any());
    verify(requestQueueRepository).getLightweightForPositioning(
      new RequestQueueKey(ITEM, itemId.toString()));

    ArgumentCaptor<Request> closedRequestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(requestRepository).update(closedRequestCaptor.capture());
    Request closedRequest = closedRequestCaptor.getValue();
    assertThat(closedRequest.getId(), is(selectedRequest.getId()));
    assertThat(closedRequest.getStatus(), is(RequestStatus.CLOSED_FILLED));
    assertThat(closedRequest.getPosition(), is(nullValue()));

    ArgumentCaptor<RequestQueue> queueCaptor = ArgumentCaptor.forClass(RequestQueue.class);
    verify(requestQueueRepository).updateRequestsWithChangedPositions(queueCaptor.capture());
    assertQueueContainsConsecutivePositions(queueCaptor.getValue(),
      initialQueue.getRequests().stream().skip(1).map(Request::getId).toList());
  }

  @Test
  void checkOutLocksRequestInstanceQueueWhenTlrIsEnabled() throws Exception {
    UUID requestInstanceId = UUID.randomUUID();
    UUID circulationItemInstanceId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    RequestQueue initialQueue = requestQueue(itemId, requestInstanceId, 2);
    Request selectedRequest = initialQueue.getRequests().iterator().next();
    RequestQueue freshQueue = copy(initialQueue);
    LoanAndRelatedRecords records = records(itemId, circulationItemInstanceId, initialQueue,
      TLR_ENABLED);

    allowSuccessfulQueueUpdate(freshQueue);

    Result<LoanAndRelatedRecords> result = updateRequestQueue
      .onCheckOut(records, selectedRequest)
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.succeeded(), is(true));
    RequestQueueKey expectedKey = new RequestQueueKey(INSTANCE, requestInstanceId.toString());
    verify(lockService).execute(eq(expectedKey), eq(lockConfiguration), any());
    verify(requestQueueRepository).getLightweightForPositioning(expectedKey);
  }

  @Test
  void checkOutDefersQueueReadAndWritesUntilLockExecutesCriticalSection() throws Exception {
    UUID itemId = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();
    RequestQueue initialQueue = requestQueue(itemId, instanceId, 2);
    Request selectedRequest = initialQueue.getRequests().iterator().next();
    LoanAndRelatedRecords records = records(itemId, instanceId, initialQueue,
      TlrSettingsConfiguration.defaultSettings());

    reset(lockService);
    when(lockService.execute(any(RequestQueueKey.class), eq(lockConfiguration), any()))
      .thenReturn(completedFuture(succeeded(records)));

    Result<LoanAndRelatedRecords> result = updateRequestQueue
      .onCheckOut(records, selectedRequest)
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.succeeded(), is(true));
    verify(lockService).execute(eq(new RequestQueueKey(ITEM, itemId.toString())),
      eq(lockConfiguration), any());
    verifyNoInteractions(requestQueueRepository, requestRepository);
  }

  @Test
  void checkOutDoesNotWriteWhenSelectedRequestLeftQueueBeforeLockWasAcquired()
    throws Exception {

    UUID itemId = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();
    RequestQueue initialQueue = requestQueue(itemId, instanceId, 2);
    Request selectedRequest = initialQueue.getRequests().iterator().next();
    RequestQueue freshQueue = new RequestQueue(initialQueue.getRequests().stream()
      .skip(1)
      .map(request -> Request.from(request.asJson()))
      .toList());
    LoanAndRelatedRecords records = records(itemId, instanceId, initialQueue,
      TlrSettingsConfiguration.defaultSettings());
    when(requestQueueRepository.getLightweightForPositioning(any(RequestQueueKey.class)))
      .thenReturn(completedFuture(succeeded(freshQueue)));

    Result<LoanAndRelatedRecords> result = updateRequestQueue
      .onCheckOut(records, selectedRequest)
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.succeeded(), is(true));
    verify(requestRepository, never()).update(any(Request.class));
    verify(requestQueueRepository, never()).updateRequestsWithChangedPositions(any());
  }

  @Test
  void checkOutDoesNotAcquireQueueLockWhenThereIsNoFulfillableRequest() throws Exception {
    UUID itemId = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();
    LoanAndRelatedRecords records = records(itemId, instanceId,
      new RequestQueue(List.of()), TlrSettingsConfiguration.defaultSettings());
    when(requestQueueService.findRequestFulfillableByItem(records.getItem(),
      records.getRequestQueue())).thenReturn(completedFuture(succeeded(null)));

    Result<LoanAndRelatedRecords> result = updateRequestQueue.onCheckOut(records)
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.succeeded(), is(true));
    verifyNoInteractions(lockService, requestQueueRepository, requestRepository);
  }

  private void allowSuccessfulQueueUpdate(RequestQueue freshQueue) {
    when(requestQueueRepository.getLightweightForPositioning(any(RequestQueueKey.class)))
      .thenReturn(completedFuture(succeeded(freshQueue)));
    when(requestRepository.update(any(Request.class)))
      .thenAnswer(invocation -> completedFuture(succeeded(invocation.getArgument(0))));
    when(requestQueueRepository.updateRequestsWithChangedPositions(any(RequestQueue.class)))
      .thenAnswer(invocation -> completedFuture(succeeded(invocation.getArgument(0))));
  }

  private LoanAndRelatedRecords records(UUID itemId, UUID instanceId, RequestQueue queue,
    TlrSettingsConfiguration tlrSettings) {

    Item item = Item.from(new JsonObject()
      .put("id", itemId.toString())
      .put("instanceId", instanceId.toString()));
    Loan loan = Loan.from(new JsonObject().put("id", UUID.randomUUID().toString()))
      .withItem(item);
    return new LoanAndRelatedRecords(loan)
      .withRequestQueue(queue)
      .withTlrSettings(tlrSettings);
  }

  private RequestQueue requestQueue(UUID itemId, UUID instanceId, int size) {
    List<Request> requests = java.util.stream.IntStream.rangeClosed(1, size)
      .mapToObj(position -> Request.from(new RequestBuilder()
        .withId(UUID.randomUUID())
        .withItemId(itemId)
        .withInstanceId(instanceId)
        .withPosition(position)
        .open()
        .page()
        .create()))
      .toList();

    return new RequestQueue(requests);
  }

  private RequestQueue copy(RequestQueue queue) {
    return new RequestQueue(queue.getRequests().stream()
      .map(request -> Request.from(request.asJson()))
      .toList());
  }

  private void assertQueueContainsConsecutivePositions(RequestQueue queue,
    List<String> expectedRequestIds) {

    assertThat(queue.getRequests().stream().map(Request::getId).toList(),
      is(expectedRequestIds));
    assertThat(queue.getRequests().stream().map(Request::getPosition).toList(),
      is(List.of(1, 2)));
  }
}
