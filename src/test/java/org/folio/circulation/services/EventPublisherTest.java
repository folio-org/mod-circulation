package org.folio.circulation.services;

import static java.util.concurrent.ForkJoinPool.commonPool;
import static org.folio.circulation.domain.EventType.LOG_RECORD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

  @Mock
  private Clients clients;
  @Mock
  private PubSubPublishingService pubSubPublishingService;
  @Mock
  private CollectionResourceClient localeClient;
  @Mock
  private GetManyRecordsClient settingsStorageClient;

  private EventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    when(clients.pubSubPublishingService()).thenReturn(pubSubPublishingService);
    when(clients.localeClient()).thenReturn(localeClient);
    when(clients.settingsStorageClient()).thenReturn(settingsStorageClient);
    when(pubSubPublishingService.publishEvent(anyString(), anyString()))
      .thenReturn(CompletableFuture.completedFuture(true));
    when(localeClient.get())
      .thenReturn(CompletableFuture.completedFuture(
        Result.failed(new ServerErrorFailure("locale not available"))));

    eventPublisher = new EventPublisher(clients);
  }

  @Test
  void renewalCirculationLogSourceShouldBeRenewalStaffNotCheckoutStaff() throws Exception {
    String checkoutStaffId = UUID.randomUUID().toString();
    String renewalStaffId = UUID.randomUUID().toString();

    RenewalContext renewalContext = buildRenewalContext(checkoutStaffId, renewalStaffId);
    eventPublisher.publishDueDateChangedEvent(renewalContext).get();
    commonPool().awaitQuiescence(5, TimeUnit.SECONDS);

    String updatedByUserId = captureLogPayloadByAction("Renewed");

    assertThat(updatedByUserId, is(renewalStaffId));
    assertThat(updatedByUserId, is(not(checkoutStaffId)));
  }

  @Test
  void dueDateChangedCirculationLogSourceShouldBeRenewalStaffNotCheckoutStaff() throws Exception {
    String checkoutStaffId = UUID.randomUUID().toString();
    String renewalStaffId = UUID.randomUUID().toString();

    RenewalContext renewalContext = buildRenewalContext(checkoutStaffId, renewalStaffId);
    eventPublisher.publishDueDateChangedEvent(renewalContext).get();
    commonPool().awaitQuiescence(5, TimeUnit.SECONDS);

    String updatedByUserId = captureLogPayloadByAction("Changed due date");

    assertThat(updatedByUserId, is(renewalStaffId));
    assertThat(updatedByUserId, is(not(checkoutStaffId)));
  }

  private RenewalContext buildRenewalContext(String checkoutStaffId, String renewalStaffId) {
    ZonedDateTime previousDueDate = ZonedDateTime.now().minusDays(7);
    ZonedDateTime newDueDate = ZonedDateTime.now().plusDays(7);

    Loan loan = Loan.from(new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("userId", UUID.randomUUID().toString())
      .put("itemId", UUID.randomUUID().toString())
      .put("action", "renewed")
      .put("dueDate", newDueDate.toString())
      .put("status", new JsonObject().put("name", "Open"))
      .put("metadata", new JsonObject()
        .put("updatedByUserId", checkoutStaffId)));
    loan.setPreviousDueDate(previousDueDate);

    return RenewalContext.create(loan, new JsonObject(), renewalStaffId);
  }

  private String captureLogPayloadByAction(String action) {
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(pubSubPublishingService, atLeastOnce())
      .publishEvent(eq(LOG_RECORD.name()), payloadCaptor.capture());

    return payloadCaptor.getAllValues().stream()
      .map(JsonObject::new)
      .filter(json -> action.equals(
        json.getJsonObject("payload", new JsonObject()).getString("action")))
      .findFirst()
      .orElseThrow(() -> new AssertionError("No '" + action + "' LOG_RECORD event was published"))
      .getJsonObject("payload")
      .getString("updatedByUserId");
  }
}
