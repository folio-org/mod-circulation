package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.runAsync;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createRequestNoticeContext;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.notice.TemplateContextUtil;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class RequestNoticeSender {

  private static final Map<RequestType, NoticeEventType> requestTypeToEventMap;

  static {
    Map<RequestType, NoticeEventType> map = new EnumMap<>(RequestType.class);
    map.put(RequestType.PAGE, NoticeEventType.PAGING_REQUEST);
    map.put(RequestType.HOLD, NoticeEventType.HOLD_REQUEST);
    map.put(RequestType.RECALL, NoticeEventType.RECALL_REQUEST);
    requestTypeToEventMap = Collections.unmodifiableMap(map);
  }

  public static RequestNoticeSender using(Clients clients) {
    return new RequestNoticeSender(PatronNoticeService.using(clients),
      RequestRepository.using(clients), new LoanRepository(clients),
      new EventPublisher(clients.pubSubPublishingService()));
  }

  private final PatronNoticeService patronNoticeService;
  private final RequestRepository requestRepository;
  private final LoanRepository loanRepository;
  private final EventPublisher eventPublisher;

  public RequestNoticeSender(PatronNoticeService patronNoticeService,
                             RequestRepository requestRepository,
                             LoanRepository loanRepository,
                             EventPublisher eventPublisher) {
    this.patronNoticeService = patronNoticeService;
    this.requestRepository = requestRepository;
    this.loanRepository = loanRepository;
    this.eventPublisher = eventPublisher;
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestCreated(
    RequestAndRelatedRecords relatedRecords) {

    Request request = relatedRecords.getRequest();

    Item item = request.getItem();
    User requester = request.getRequester();
    NoticeEventType eventType =
      requestTypeToEventMap.getOrDefault(request.getRequestType(), NoticeEventType.UNKNOWN);
    PatronNoticeEvent requestCreatedEvent = new PatronNoticeEventBuilder()
      .withItem(item)
      .withUser(requester)
      .withEventType(eventType)
      .withNoticeContext(createRequestNoticeContext(request))
      .build();
    patronNoticeService.acceptNoticeEvent(requestCreatedEvent, NoticeLogContext.from(request));

    Loan loan = request.getLoan();
    if (request.getRequestType() == RequestType.RECALL && loan != null) {
      sendNoticeOnItemRecalledEvent(loan);
      sendLogEvent(loan);
    }
    return Result.succeeded(relatedRecords);
  }

  private void sendLogEvent(Loan loan) {
    runAsync(() -> loanRepository.getById(loan.getId())
      .thenAccept(existingLoan -> existingLoan.map(l -> loan.setPreviousDueDate(l.getDueDate())))
      .thenApply(vVoid -> eventPublisher.publishRecallRequestedEvent(loan)));
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestMoved(
    RequestAndRelatedRecords relatedRecords) {

    Request request = relatedRecords.getRequest();

    Loan loan = request.getLoan();
    if (request.getRequestType() == RequestType.RECALL && loan != null) {
      sendNoticeOnItemRecalledEvent(loan);
      sendLogEvent(loan);
    }
    return Result.succeeded(relatedRecords);
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestUpdated(
    RequestAndRelatedRecords relatedRecords) {
    if (relatedRecords.getRequest().getStatus() == RequestStatus.CLOSED_CANCELLED) {
      requestRepository.loadCancellationReason(relatedRecords.getRequest())
        .thenApply(r -> r.map(relatedRecords::withRequest))
        .thenAccept(r -> r.next(this::sendNoticeOnRequestCancelled));
    }
    return Result.succeeded(relatedRecords);
  }

  private Result<Void> sendNoticeOnRequestCancelled(RequestAndRelatedRecords relatedRecords) {
    Request request = relatedRecords.getRequest();
    Item item = request.getItem();
    User requester = request.getRequester();

    PatronNoticeEvent requestCancelledEvent = new PatronNoticeEventBuilder()
      .withItem(item)
      .withUser(requester)
      .withEventType(NoticeEventType.REQUEST_CANCELLATION)
      .withNoticeContext(createRequestNoticeContext(request))
      .build();
    patronNoticeService.acceptNoticeEvent(requestCancelledEvent, NoticeLogContext.from(request));

    return Result.succeeded(null);
  }

  private Result<Void> sendNoticeOnItemRecalledEvent(Loan loan) {
    if (loan.getUser() != null && loan.getItem() != null) {
      PatronNoticeEvent itemRecalledEvent = new PatronNoticeEventBuilder()
        .withItem(loan.getItem())
        .withUser(loan.getUser())
        .withEventType(NoticeEventType.ITEM_RECALLED)
        .withNoticeContext(TemplateContextUtil.createLoanNoticeContext(loan))
        .build();
      patronNoticeService.acceptNoticeEvent(itemRecalledEvent, NoticeLogContext.from(loan));
    }
    return Result.succeeded(null);
  }

}


