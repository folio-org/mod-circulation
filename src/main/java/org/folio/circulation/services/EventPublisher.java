package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.EventType.ITEM_CHECKED_IN;
import static org.folio.circulation.domain.EventType.ITEM_CHECKED_OUT;
import static org.folio.circulation.domain.EventType.ITEM_DECLARED_LOST;
import static org.folio.circulation.domain.EventType.LOAN_DUE_DATE_CHANGED;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.EventMetadata;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.util.pubsub.PubSubClientUtils;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class EventPublisher {
  private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);

  public static final String USER_ID_FIELD = "userId";
  public static final String LOAN_ID_FIELD = "loanId";
  public static final String DUE_DATE_FIELD = "dueDate";
  public static final String RETURN_DATE_FIELD = "returnDate";
  public static final String DUE_DATE_CHANGED_BY_RECALL_FIELD = "dueDateChangedByRecall";

  private final Map<String, String> okapiHeaders;
  private final Context vertxContext;

  private Clients clients;

  public EventPublisher(RoutingContext routingContext) {
    okapiHeaders = routingContext.request().headers().entries().stream()
      .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));
    vertxContext = routingContext.vertx().getOrCreateContext();
  }

  public EventPublisher(RoutingContext routingContext, Clients circulationClients) {
    this(routingContext);
    clients = circulationClients;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> publishItemCheckedOutEvent(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    if (loanAndRelatedRecords.getLoan() != null) {
      Loan loan = loanAndRelatedRecords.getLoan();

      JsonObject payloadJsonObject = new JsonObject();
      write(payloadJsonObject, USER_ID_FIELD, loan.getUserId());
      write(payloadJsonObject, LOAN_ID_FIELD, loan.getId());
      write(payloadJsonObject, DUE_DATE_FIELD, loan.getDueDate());

      publishEvent(ITEM_CHECKED_OUT.name(), payloadJsonObject.encode());
    }
    else {
      logger.error("Failed to publish {} event: loan is null", ITEM_CHECKED_OUT.name());
    }

    return completedFuture(succeeded(loanAndRelatedRecords));
  }

  public CompletableFuture<Result<CheckInProcessRecords>> publishItemCheckedInEvent(
    CheckInProcessRecords checkInProcessRecords) {

    if (checkInProcessRecords.getLoan() != null) {
      Loan loan = checkInProcessRecords.getLoan();

      JsonObject payloadJsonObject = new JsonObject();
      write(payloadJsonObject, USER_ID_FIELD, loan.getUserId());
      write(payloadJsonObject, LOAN_ID_FIELD, loan.getId());
      write(payloadJsonObject, RETURN_DATE_FIELD, loan.getReturnDate());

      publishEvent(ITEM_CHECKED_IN.name(), payloadJsonObject.encode());
    }
    else {
      logger.error("Failed to publish {} event: loan is null", ITEM_CHECKED_IN.name());
    }

    return completedFuture(succeeded(checkInProcessRecords));
  }

  public CompletableFuture<Result<Loan>> publishDeclaredLostEvent(Loan loan) {
    if (loan != null) {
      JsonObject payloadJsonObject = new JsonObject();
      write(payloadJsonObject, USER_ID_FIELD, loan.getUserId());
      write(payloadJsonObject, LOAN_ID_FIELD, loan.getId());

      publishEvent(ITEM_DECLARED_LOST.name(), payloadJsonObject.encode());
    }

    return completedFuture(succeeded(loan));
  }

  private CompletableFuture<Result<Loan>> publishDueDateChangedEvent(Loan loan) {
    if (loan != null) {
      JsonObject payloadJsonObject = new JsonObject();
      write(payloadJsonObject, USER_ID_FIELD, loan.getUserId());
      write(payloadJsonObject, LOAN_ID_FIELD, loan.getId());
      write(payloadJsonObject, DUE_DATE_FIELD, loan.getDueDate());
      write(payloadJsonObject, DUE_DATE_CHANGED_BY_RECALL_FIELD, loan.wasDueDateChangedByRecall());

      publishEvent(LOAN_DUE_DATE_CHANGED.name(), payloadJsonObject.encode());
    }

    return completedFuture(succeeded(loan));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> publishDueDateChangedEvent(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    if (loanAndRelatedRecords.getLoan() != null) {
      Loan loan = loanAndRelatedRecords.getLoan();
      publishDueDateChangedEvent(loan);
    }

    return completedFuture(succeeded(loanAndRelatedRecords));
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> publishDueDateChangedEvent(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    LoanRepository loanRepository = new LoanRepository(clients);
    loanRepository.findOpenLoanForRequest(requestAndRelatedRecords.getRequest())
      .thenCompose(r -> r.after(this::publishDueDateChangedEvent));

    return completedFuture(succeeded(requestAndRelatedRecords));
  }

  private void publishEvent(String eventType, String payload) {
    Event event = new Event()
      .withId(UUID.randomUUID().toString())
      .withEventType(eventType)
      .withEventPayload(payload)
      .withEventMetadata(new EventMetadata()
        .withPublishedBy(PubSubClientUtils.constructModuleName())
        .withTenantId(okapiHeaders.get(OKAPI_TENANT_HEADER))
        .withEventTTL(1));

    OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders, vertxContext.owner());

    PubSubClientUtils.sendEventMessage(event, params)
      .whenComplete((result, throwable) -> {
        if (Boolean.TRUE.equals(result)) {
          logger.debug("Event published successfully. ID: {}, type: {}, payload: {}",
            event.getId(), event.getEventType(), event.getEventPayload());
        } else {
          logger.error("Failed to publish event. ID: {}, type: {}, payload: {}", throwable,
            event.getId(), event.getEventType(), event.getEventPayload());
        }
      });
  }
}
