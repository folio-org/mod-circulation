package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.EventType.ITEM_CHECKED_IN;
import static org.folio.circulation.domain.EventType.ITEM_CHECKED_OUT;
import static org.folio.circulation.domain.EventType.ITEM_CLAIMED_RETURNED;
import static org.folio.circulation.domain.EventType.ITEM_DECLARED_LOST;
import static org.folio.circulation.domain.EventType.LOAN_DUE_DATE_CHANGED;
import static org.folio.circulation.domain.EventType.LOG_RECORD;
import static org.folio.circulation.domain.LoanAction.CHECKED_IN;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.LOG_EVENT_TYPE;
import static org.folio.circulation.domain.representations.logs.CirculationCheckInCheckOutLogEventMapper.mapToCheckInLogEventJson;
import static org.folio.circulation.domain.representations.logs.CirculationCheckInCheckOutLogEventMapper.mapToCheckOutLogEventJson;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.PAYLOAD;
import static org.folio.circulation.domain.representations.logs.LogEventType.LOAN;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.domain.representations.logs.RequestUpdateLogEventMapper.mapToRequestLogEventJson;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.results.Result.succeeded;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.EventType;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.domain.representations.logs.LoanLogContext;
import org.folio.circulation.domain.representations.logs.LogContextActionResolver;
import org.folio.circulation.domain.representations.logs.LogEventType;
import org.folio.circulation.domain.Request;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import java.util.concurrent.CompletableFuture;

public class EventPublisher {
  private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);

  public static final String USER_ID_FIELD = "userId";
  public static final String LOAN_ID_FIELD = "loanId";
  public static final String DUE_DATE_FIELD = "dueDate";
  public static final String RETURN_DATE_FIELD = "returnDate";
  public static final String DUE_DATE_CHANGED_BY_RECALL_FIELD = "dueDateChangedByRecall";
  public static final String FAILED_TO_PUBLISH_LOG_TEMPLATE =
    "Failed to publish {} event: loan is null";

  private final PubSubPublishingService pubSubPublishingService;

  public EventPublisher(RoutingContext routingContext) {
    pubSubPublishingService = new PubSubPublishingService(routingContext);
  }

  public EventPublisher(PubSubPublishingService pubSubPublishingService) {
    this.pubSubPublishingService = pubSubPublishingService;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> publishItemCheckedOutEvent(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    if (loanAndRelatedRecords.getLoan() != null) {
      Loan loan = loanAndRelatedRecords.getLoan();

      JsonObject payloadJsonObject = new JsonObject();
      write(payloadJsonObject, USER_ID_FIELD, loan.getUserId());
      write(payloadJsonObject, LOAN_ID_FIELD, loan.getId());
      write(payloadJsonObject, DUE_DATE_FIELD, loan.getDueDate());

      JsonObject logEventPayload = mapToCheckOutLogEventJson(loanAndRelatedRecords);
      CompletableFuture.runAsync(() -> pubSubPublishingService.publishEvent(LOG_RECORD.name(), logEventPayload.encode()));

      return pubSubPublishingService.publishEvent(ITEM_CHECKED_OUT.name(), payloadJsonObject.encode())
        .thenApply(r -> succeeded(loanAndRelatedRecords));
    }
    else {
      logger.error(FAILED_TO_PUBLISH_LOG_TEMPLATE, ITEM_CHECKED_OUT.name());
    }

    return completedFuture(succeeded(loanAndRelatedRecords));
  }

  public CompletableFuture<Result<CheckInContext>> publishItemCheckedInEvents(
    CheckInContext checkInContext) {

    if (checkInContext.getLoan() != null) {
      Loan loan = checkInContext.getLoan();

      JsonObject payloadJsonObject = new JsonObject();
      write(payloadJsonObject, USER_ID_FIELD, loan.getUserId());
      write(payloadJsonObject, LOAN_ID_FIELD, loan.getId());
      write(payloadJsonObject, RETURN_DATE_FIELD, loan.getReturnDate());

      JsonObject logEventPayload = mapToCheckInLogEventJson(checkInContext);
      CompletableFuture.runAsync(() -> pubSubPublishingService.publishEvent(LOG_RECORD.name(), logEventPayload.encode()));

      return
        pubSubPublishingService.publishEvent(ITEM_CHECKED_IN.name(),
        payloadJsonObject.encode())
        .thenApply(r -> succeeded(checkInContext));
    }
    else {
      logger.error(FAILED_TO_PUBLISH_LOG_TEMPLATE, ITEM_CHECKED_IN.name());
    }

    return completedFuture(succeeded(checkInContext));
  }

  public CompletableFuture<Result<Loan>> publishDeclaredLostEvent(Loan loan) {
    return publishStatusChangeEvent(ITEM_DECLARED_LOST, loan);
  }

  public CompletableFuture<Result<Loan>> publishItemClaimedReturnedEvent(Loan loan) {
    return publishStatusChangeEvent(ITEM_CLAIMED_RETURNED, loan);
  }

  private CompletableFuture<Result<Loan>> publishStatusChangeEvent(EventType eventType, Loan loan) {
    final String eventName = eventType.name();

    if (loan == null) {
      logger.error(FAILED_TO_PUBLISH_LOG_TEMPLATE, eventName);
      return completedFuture(succeeded(null));
    }

    LoanLogContext loanLogContext = LoanLogContext.from(loan)
      .withDescription(String.format("Additional information: %s", loan.getActionComment()));
    CompletableFuture.runAsync(() -> publishLogRecord(loanLogContext.asJson(), LOAN));

    JsonObject payloadJson = new JsonObject();
    write(payloadJson, USER_ID_FIELD, loan.getUserId());
    write(payloadJson, LOAN_ID_FIELD, loan.getId());

    return pubSubPublishingService.publishEvent(eventName, payloadJson.encode())
      .thenApply(r -> succeeded(loan));
  }

  private CompletableFuture<Result<Loan>> publishDueDateChangedEvent(Loan loan) {
    if (loan != null) {
      JsonObject payloadJsonObject = new JsonObject();
      write(payloadJsonObject, USER_ID_FIELD, loan.getUserId());
      write(payloadJsonObject, LOAN_ID_FIELD, loan.getId());
      write(payloadJsonObject, DUE_DATE_FIELD, loan.getDueDate());
      write(payloadJsonObject, DUE_DATE_CHANGED_BY_RECALL_FIELD, loan.wasDueDateChangedByRecall());

      LoanLogContext loanLogContext = LoanLogContext.from(loan)
        .withAction(LogContextActionResolver.resolveAction(loan.getAction()))
        .withDescription(String.format("New due date: %s (from %s)",
          loan.getDueDate(), loan.getOriginalDueDate()));
      CompletableFuture.runAsync(() -> publishLogRecord(loanLogContext.asJson(), LOAN));

      return pubSubPublishingService.publishEvent(LOAN_DUE_DATE_CHANGED.name(),
        payloadJsonObject.encode())
        .thenApply(r -> succeeded(loan));
    }
    else {
      logger.error(FAILED_TO_PUBLISH_LOG_TEMPLATE, LOAN_DUE_DATE_CHANGED.name());
    }

    return completedFuture(succeeded(null));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> publishDueDateChangedEvent(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    if (loanAndRelatedRecords.getLoan() != null) {
      Loan loan = loanAndRelatedRecords.getLoan();
      publishDueDateChangedEvent(loan);
    }

    return completedFuture(succeeded(loanAndRelatedRecords));
  }

  public CompletableFuture<Result<RenewalContext>> publishDueDateChangedEvent(
    RenewalContext renewalContext) {

    publishDueDateChangedEvent(renewalContext.getLoan());

    return completedFuture(succeeded(renewalContext));
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> publishDueDateChangedEvent(
    RequestAndRelatedRecords requestAndRelatedRecords, Clients clients) {

    LoanRepository loanRepository = new LoanRepository(clients);
    loanRepository.findOpenLoanForRequest(requestAndRelatedRecords.getRequest())
      .thenCompose(r -> r.after(this::publishDueDateChangedEvent));

    return completedFuture(succeeded(requestAndRelatedRecords));
  }

  public CompletableFuture<Result<Void>> publishAgedToLostEvent(Loan loan) {
    return publishLogRecord(LoanLogContext.from(loan)
      .withDescription(String.format("Due date: %s", loan.getAgedToLostDateTime())).asJson(), LOAN);
  }

  public CompletableFuture<Result<Void>> publishClosedLoanEvent(Loan loan) {
    if (!CHECKED_IN.getValue().equalsIgnoreCase(loan.getAction())) {
      return publishLogRecord(LoanLogContext.from(loan)
        .withServicePointId(loan.getCheckoutServicePointId()).asJson(), LOAN);
    }
    return CompletableFuture.completedFuture(succeeded(null));
  }

  public CompletableFuture<Result<Loan>> publishMarkedAsMissingLoanEvent(Loan loan) {
    return publishLogRecord(LoanLogContext.from(loan)
      .withDescription(String.format("Additional information: %s", loan.getActionComment())).asJson(), LOAN)
      .thenApply(r -> r.map(v -> loan));
  }

  public CompletableFuture<Result<LoanAnonymizationRecords>> publishAnonymizeEvents(LoanAnonymizationRecords records) {
    return allOf(records.getAnonymizedLoans(), this::publishAnonymizeEvent)
      .thenApply(r -> succeeded(records));
  }

  public CompletableFuture<Result<Void>> publishAnonymizeEvent(Loan loan) {
    return publishLogRecord(LoanLogContext.from(loan).withAction("Anonymize").withDescription("").asJson(), LOAN);
  }

  public CompletableFuture<Result<Void>> publishRecallRequestedEvent(Loan loan) {
    return publishLogRecord(LoanLogContext.from(loan)
      .withAction("Recall requested")
      .withDescription(String.format("New due date: %s (from %s)", loan.getDueDate(), loan.getOriginalDueDate())).asJson(), LOAN);
  }

  public CompletableFuture<Result<Void>> publishLogRecord(JsonObject context, LogEventType payloadType) {
    if (NOTICE.equals(payloadType)) {
      context = new JsonObject().put(PAYLOAD.value(), context.encode());
    }
    write(context, LOG_EVENT_TYPE.value(), payloadType.value());
    return pubSubPublishingService.publishEvent(LOG_RECORD.name(), context.encode())
      .thenApply(r -> succeeded(null));
  }

  public RequestAndRelatedRecords publishLogRecordAsync(RequestAndRelatedRecords requestAndRelatedRecords, Request originalRequest, LogEventType logEventType) {
    CompletableFuture.runAsync(() -> publishLogRecord(mapToRequestLogEventJson(originalRequest, requestAndRelatedRecords.getRequest()), logEventType));
    return requestAndRelatedRecords;
  }
}
