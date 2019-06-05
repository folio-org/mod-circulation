package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.NoticeContextUtil.createRequestNoticeContext;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LocationRepository;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.NoticeContextUtil;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

public class RequestNoticeSender {

  private static Map<RequestType, NoticeEventType> requestTypeToEventMap;

  static {
    Map<RequestType, NoticeEventType> map = new EnumMap<>(RequestType.class);
    map.put(RequestType.PAGE, NoticeEventType.PAGING_REQUEST);
    map.put(RequestType.HOLD, NoticeEventType.HOLD_REQUEST);
    map.put(RequestType.RECALL, NoticeEventType.RECALL_REQUEST);
    requestTypeToEventMap = Collections.unmodifiableMap(map);
  }

  public static RequestNoticeSender using(Clients clients) {
    return new RequestNoticeSender(
      PatronNoticeService.using(clients),
      RequestRepository.using(clients),
      LocationRepository.using(clients)
    );
  }

  private final PatronNoticeService patronNoticeService;
  private final RequestRepository requestRepository;
  private final LocationRepository locationRepository;

  public RequestNoticeSender(PatronNoticeService patronNoticeService,
                             RequestRepository requestRepository,
                             LocationRepository locationRepository) {
    this.patronNoticeService = patronNoticeService;
    this.requestRepository = requestRepository;
    this.locationRepository = locationRepository;
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
      .withTiming(NoticeTiming.UPON_AT)
      .withNoticeContext(createRequestNoticeContext(request))
      .build();
    patronNoticeService.acceptNoticeEvent(requestCreatedEvent);

    Loan loan = request.getLoan();
    if (request.getRequestType() == RequestType.RECALL &&
      loan != null && loan.hasDueDateChanged()) {

      PatronNoticeEvent itemRecalledEvent = new PatronNoticeEventBuilder()
        .withItem(loan.getItem())
        .withUser(loan.getUser())
        .withEventType(NoticeEventType.RECALL_TO_LOANEE)
        .withTiming(NoticeTiming.UPON_AT)
        .withNoticeContext(NoticeContextUtil.createLoanNoticeContext(loan, null))
        .build();
      patronNoticeService.acceptNoticeEvent(itemRecalledEvent);
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
      .withTiming(NoticeTiming.UPON_AT)
      .withNoticeContext(createRequestNoticeContext(request))
      .build();
    patronNoticeService.acceptNoticeEvent(requestCancelledEvent);

    return Result.succeeded(null);
  }

}


