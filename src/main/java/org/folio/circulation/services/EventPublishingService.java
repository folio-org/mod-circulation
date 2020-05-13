package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
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
import org.folio.circulation.resources.TenantAPI;
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

public class EventPublishingService {
  private static final Logger logger = LoggerFactory.getLogger(TenantAPI.class);

  public static final String ITEM_CHECKED_OUT_EVENT_TYPE = "ITEM_CHECKED_OUT";
  public static final String ITEM_CHECKED_IN_EVENT_TYPE = "ITEM_CHECKED_IN";
  public static final String ITEM_DECLARED_LOST_EVENT_TYPE = "ITEM_DECLARED_LOST";
  public static final String LOAN_DUE_DATE_UPDATED_EVENT_TYPE = "LOAN_DUE_DATE_UPDATED";

  private final Map<String, String> okapiHeaders;
  private final Context vertxContext;

  private Clients clients;

  public EventPublishingService(RoutingContext routingContext) {
    okapiHeaders = routingContext.request().headers().entries().stream()
      .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));
    vertxContext = routingContext.vertx().getOrCreateContext();
  }

  public EventPublishingService(RoutingContext routingContext, Clients circulationClients) {
    this(routingContext);
    clients = circulationClients;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> publishItemCheckedOutEvent(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    if (loanAndRelatedRecords.getLoan() != null) {
      Loan loan = loanAndRelatedRecords.getLoan();

      JsonObject payloadJsonObject = new JsonObject();
      write(payloadJsonObject, "userId", loan.getUserId());
      write(payloadJsonObject, "loanId", loan.getId());
      write(payloadJsonObject, "dueDate", loan.getDueDate());

      publishEvent(ITEM_CHECKED_OUT_EVENT_TYPE, payloadJsonObject.encode());
    }
    else {
      logger.error("Failed to publish {} event: loan is null", ITEM_CHECKED_OUT_EVENT_TYPE);
    }

    return completedFuture(succeeded(loanAndRelatedRecords));
  }

  public CompletableFuture<Result<CheckInProcessRecords>> publishItemCheckedInEvent(
    CheckInProcessRecords checkInProcessRecords) {

    if (checkInProcessRecords.getLoan() != null) {
      Loan loan = checkInProcessRecords.getLoan();

      JsonObject payloadJsonObject = new JsonObject();
      write(payloadJsonObject, "userId", loan.getUserId());
      write(payloadJsonObject, "loanId", loan.getId());
      write(payloadJsonObject, "returnDate", loan.getReturnDate());

      publishEvent(ITEM_CHECKED_IN_EVENT_TYPE, payloadJsonObject.encode());
    }
    else {
      logger.error("Failed to publish {} event: loan is null", ITEM_CHECKED_IN_EVENT_TYPE);
    }

    return completedFuture(succeeded(checkInProcessRecords));
  }

  public CompletableFuture<Result<Loan>> publishDeclaredLostEvent(Loan loan) {
    if (loan != null) {
      JsonObject payloadJsonObject = new JsonObject();
      write(payloadJsonObject, "userId", loan.getUserId());
      write(payloadJsonObject, "loanId", loan.getId());

      publishEvent(ITEM_DECLARED_LOST_EVENT_TYPE, payloadJsonObject.encode());
    }

    return completedFuture(succeeded(loan));
  }

  private CompletableFuture<Result<Loan>> publishDueDateChangedEvent(Loan loan) {
    if (loan != null) {
      JsonObject payloadJsonObject = new JsonObject();
      write(payloadJsonObject, "userId", loan.getUserId());
      write(payloadJsonObject, "loanId", loan.getId());
      write(payloadJsonObject, "dueDate", loan.getDueDate());
      write(payloadJsonObject, "dueDateChangedByRecall", loan.wasDueDateChangedByRecall());

      publishEvent(LOAN_DUE_DATE_UPDATED_EVENT_TYPE, payloadJsonObject.encode());
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
        if (result) {
          logger.debug("Event published successfully: {}", event.getId());
        } else {
          logger.error("Failed to publish event: {}", event.getId());
        }
      });
  }
}
