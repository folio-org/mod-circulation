package org.folio.circulation.services;

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

import org.folio.circulation.domain.Loan;
import org.folio.circulation.resources.context.RenewalContext;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

  @Mock private PubSubPublishingService pubSubPublishingService;

  private EventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    when(pubSubPublishingService.publishEvent(anyString(), anyString()))
      .thenReturn(CompletableFuture.completedFuture(true));

    eventPublisher = new EventPublisher(pubSubPublishingService);
  }

  @Test
  void renewalCirculationLogSourceShouldBeRenewalStaffNotCheckoutStaff() throws Exception {
    String checkoutStaffId = UUID.randomUUID().toString();
    String renewalStaffId = UUID.randomUUID().toString();

    RenewalContext renewalContext = buildRenewalContext(checkoutStaffId, renewalStaffId);
    eventPublisher.publishDueDateChangedEvent(renewalContext).get();
    Awaitility.await()
      .atMost(Durations.FIVE_SECONDS)
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> {
        String renewedUserId = captureLogPayloadByAction("Renewed");

        assertThat(renewedUserId, is(renewalStaffId));
        assertThat(renewedUserId, is(not(checkoutStaffId)));

        String dueDateChangedUserId = captureLogPayloadByAction("Changed due date");

        assertThat(dueDateChangedUserId, is(renewalStaffId));
        assertThat(dueDateChangedUserId, is(not(checkoutStaffId)));
      });
  }

  @Test
  void dueDateChangedCirculationLogSourceShouldBeRenewalStaffNotCheckoutStaff() throws Exception {
    String checkoutStaffId = UUID.randomUUID().toString();
    String renewalStaffId = UUID.randomUUID().toString();

    RenewalContext renewalContext = buildRenewalContext(checkoutStaffId, renewalStaffId);
    eventPublisher.publishDueDateChangedEvent(renewalContext).get();

    Awaitility.await()
      .atMost(Durations.FIVE_SECONDS)
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> {
        String updatedByUserId = captureLogPayloadByAction("Changed due date");

        assertThat(updatedByUserId, is(renewalStaffId));
        assertThat(updatedByUserId, is(not(checkoutStaffId)));
      });
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
