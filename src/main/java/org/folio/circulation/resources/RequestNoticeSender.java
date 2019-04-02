package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.NoticeContextUtil.createRequestNoticeContext;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.NoticeContextUtil;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
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

  private final PatronNoticeService patronNoticeService;

  public RequestNoticeSender(PatronNoticeService patronNoticeService) {
    this.patronNoticeService = patronNoticeService;
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

    if (request.getRequestType() == RequestType.RECALL &&
      request.getLoan() != null) {
      Loan loan = request.getLoan();

      PatronNoticeEvent itemRecalledEvent = new PatronNoticeEventBuilder()
        .withItem(loan.getItem())
        .withUser(loan.getUser())
        .withEventType(NoticeEventType.RECALL_TO_LOANEE)
        .withTiming(NoticeTiming.UPON_AT)
        .withNoticeContext(NoticeContextUtil.createLoanNoticeContext(loan))
        .build();
      patronNoticeService.acceptNoticeEvent(itemRecalledEvent);
    }
    return Result.succeeded(relatedRecords);
  }


  public Result<RequestAndRelatedRecords> sendNoticeOnRequestUpdated(
    RequestAndRelatedRecords relatedRecords) {
    if (relatedRecords.getRequest().getStatus() == RequestStatus.CLOSED_CANCELLED) {
      sendNoticeOnRequestCancelled(relatedRecords);
    }
    return Result.succeeded(relatedRecords);
  }

  private void sendNoticeOnRequestCancelled(RequestAndRelatedRecords relatedRecords) {
    Request request = relatedRecords.getRequest();
    Item item = request.getItem();
    User requester = request.getRequester();

    PatronNoticeEvent requestCanceledEvent = new PatronNoticeEventBuilder()
      .withItem(item)
      .withUser(requester)
      .withEventType(NoticeEventType.REQUEST_CANCELLATION)
      .withTiming(NoticeTiming.UPON_AT)
      .withNoticeContext(createRequestNoticeContext(request))
      .build();
    patronNoticeService.acceptNoticeEvent(requestCanceledEvent);
  }


}


