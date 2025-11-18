package org.folio.circulation.domain.anonymization;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestFulfillmentPreference;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.services.RequestAnonymizationService;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.invocation.InvocationOnMock;

import java.util.UUID;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnonymizeRequestTests {

  @Mock
  RequestRepository requestRepository;

  @Mock
  Clients client;

  @Mock
  EventPublisher eventPublisher;

  RequestAnonymizationService service;

  @BeforeEach
  void setUp() {
    service = new RequestAnonymizationService(requestRepository, eventPublisher);
  }

  @Test
  void anonymizeSingleHappyPathRemovesPIIUpdatesRepoAndLogs() {
    // given a closed request with PII
    String id = UUID.randomUUID().toString();

    JsonObject json = new JsonObject()
      .put("id", id)
      .put("status", RequestStatus.CLOSED_FILLED.getValue())
      .put("fulfillmentPreference",
        RequestFulfillmentPreference.DELIVERY.getValue())
      .put("requesterId", "r-1")
      .put("proxyUserId", "p-1")
      .put("requester", new JsonObject().put("barcode", "rb"))
      .put("proxy", new JsonObject().put("barcode", "pb"))
      .put("deliveryAddressTypeId", "addr-type");

    Request request = Request.from(json);

    when(requestRepository.getById(id))
      .thenReturn(completedFuture(succeeded(request)));

    when(requestRepository.update(any(Request.class)))
      .thenAnswer((InvocationOnMock invocation) ->
        completedFuture(succeeded(invocation.getArgument(0)))
      );

    when(eventPublisher.publishRequestAnonymizedLog(any(Request.class)))
      .thenReturn(completedFuture(succeeded(null)));

    var result = service.anonymizeSingle(id, "user-123").join();

    assertTrue(result.succeeded());
    assertThat(result.value(), is(id));

    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    verify(requestRepository).update(captor.capture());

    JsonObject stored = captor.getValue().asJson();

    assertTrue(stored.containsKey("requesterId"));
    assertNull(stored.getValue("requesterId"));
    assertTrue(stored.containsKey("proxyUserId"));
    assertNull(stored.getValue("proxyUserId"));
    assertFalse(stored.containsKey("requester"));
    assertFalse(stored.containsKey("proxy"));
    assertFalse(stored.containsKey("deliveryAddressTypeId"));
  }
}
