package api.requests;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.*;

import org.folio.circulation.domain.*;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.RequestAnonymizationService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import api.support.APITests;

class RequestAnonymizationServiceTest extends APITests {

  private Clients clients;
  private RequestRepository requestRepository;
  private EventPublisher eventPublisher;
  private RequestAnonymizationService service;

  @BeforeEach
  void setUp() {
    clients = mock(Clients.class);
    requestRepository = mock(RequestRepository.class);
    eventPublisher = mock(EventPublisher.class);

    when(clients.requestsStorage()).thenReturn(null);
    service = new RequestAnonymizationService(clients, eventPublisher) {
      {
        try {
          var f = RequestAnonymizationService.class.getDeclaredField("requestRepository");
          f.setAccessible(true);
          f.set(this, requestRepository);
        } catch (Exception e) { throw new RuntimeException(e); }
      }
    };
  }

  private static Request closedFilledRequest(String id) {
    var json = new io.vertx.core.json.JsonObject()
      .put("id", id)
      .put("status", RequestStatus.CLOSED_FILLED.getValue())
      .put("requesterId", "r1")
      .put("proxyUserId", "p1")
      .put("requester", new io.vertx.core.json.JsonObject().put("barcode", "rb"))
      .put("proxy", new io.vertx.core.json.JsonObject().put("barcode", "pb"))
      .put("fulfillmentPreference", RequestFulfillmentPreference.HOLD_SHELF.getValue());
    return Request.from(json);
  }

  private static Request closedDeliveryRequest(String id) {
    var json = new io.vertx.core.json.JsonObject()
      .put("id", id)
      .put("status", RequestStatus.CLOSED_CANCELLED.getValue())
      .put("requesterId", "r1")
      .put("proxyUserId", "p1")
      .put("requester", new io.vertx.core.json.JsonObject())
      .put("proxy", new io.vertx.core.json.JsonObject())
      .put("fulfillmentPreference", RequestFulfillmentPreference.DELIVERY.getValue())
      .put("deliveryAddress", new io.vertx.core.json.JsonObject().put("x", 1))
      .put("deliveryAddressTypeId", "addrType");
    return Request.from(json);
  }

  @Test
 void anonymizeSingle_happyPath_removesPII_updates_repo_and_logs() {
    String id = UUID.randomUUID().toString();
    var req = closedFilledRequest(id);

    when(requestRepository.getById(id)).thenReturn(completedFuture(Result.succeeded(req)));
    when(requestRepository.update(org.mockito.ArgumentMatchers.<Request>any()))
      .thenAnswer(inv -> completedFuture(Result.succeeded(inv.getArgument(0))));
    when(eventPublisher.publishRequestAnonymizedLog(org.mockito.ArgumentMatchers.any()))
      .thenReturn(completedFuture(Result.succeeded(null)));

    var out = service.anonymizeSingle(id, "user-123").join();

    assertTrue(out.succeeded());
    assertEquals(id, out.value());

    ArgumentCaptor<Request> updatedCap = ArgumentCaptor.forClass(Request.class);
    verify(requestRepository).update(updatedCap.capture());
    var updatedJson = updatedCap.getValue().asJson();
    assertFalse(updatedJson.containsKey("requesterId"));
    assertFalse(updatedJson.containsKey("proxyUserId"));
    assertFalse(updatedJson.containsKey("requester"));
    assertFalse(updatedJson.containsKey("proxy"));

    verify(eventPublisher).publishRequestAnonymizedLog(any());
  }



  @Test
  void anonymizeSingle_delivery_removesDeliveryFields() {
    String id = UUID.randomUUID().toString();
    var req = closedDeliveryRequest(id);

    when(requestRepository.getById(id)).thenReturn(completedFuture(Result.succeeded(req)));
    when(requestRepository.update((Request) any()))
      .thenAnswer(inv -> completedFuture(Result.succeeded(inv.getArgument(0))));
    when(eventPublisher.publishRequestAnonymizedLog(any()))
      .thenReturn(completedFuture(Result.succeeded(null)));

    var out = service.anonymizeSingle(id, "user-123").join();
    assertTrue(out.succeeded());

    ArgumentCaptor<Request> updatedCap = ArgumentCaptor.forClass(Request.class);
    verify(requestRepository).update(updatedCap.capture());
    var updatedJson = updatedCap.getValue().asJson();
    assertFalse(updatedJson.containsKey("deliveryAddress"));
    assertFalse(updatedJson.containsKey("deliveryAddressTypeId"));
  }

  @Test
 void anonymizeSingle_returns404_whenRequestNotFound() {
    String id = UUID.randomUUID().toString();

    when(requestRepository.getById(id)).thenReturn(completedFuture(Result.succeeded(null)));

    var out = service.anonymizeSingle(id, "user-123").join();

    assertFalse(out.succeeded());
    assertTrue(out.cause().toString().contains("cannot be found")); // RecordNotFoundFailure message
    verify(requestRepository, never()).update((Request) any());
    verify(eventPublisher, never()).publishRequestAnonymizedLog(any());
  }

  @Test
void anonymizeSingle_returns422_whenRequestIsOpen() {
    String id = UUID.randomUUID().toString();
    var json = new io.vertx.core.json.JsonObject()
      .put("id", id)
      .put("status", RequestStatus.OPEN_NOT_YET_FILLED.getValue());
    var req = Request.from(json);

    when(requestRepository.getById(id)).thenReturn(completedFuture(Result.succeeded(req)));

    var out = service.anonymizeSingle(id, "user-123").join();

    assertFalse(out.succeeded());
    assertTrue(out.cause().toString().contains("requestNotClosed"));
    verify(requestRepository, never()).update((Request) any());
    verify(eventPublisher, never()).publishRequestAnonymizedLog(any());
  }

  @Test
 void anonymizeSingle_returns422_whenIdIsNotUuid() {
    var out = service.anonymizeSingle("not-a-uuid", "user-123").join();
    assertFalse(out.succeeded());
    assertTrue(out.cause().toString().contains("invalidRequestId"));
  }
}
