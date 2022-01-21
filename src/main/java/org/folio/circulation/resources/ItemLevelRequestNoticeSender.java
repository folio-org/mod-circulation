package org.folio.circulation.resources;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class ItemLevelRequestNoticeSender extends RequestNoticeSender {
  public ItemLevelRequestNoticeSender(Clients clients) {
    super(clients);
  }

  @Override
  public Result<RequestAndRelatedRecords> sendNoticeOnRequestCreated(
    RequestAndRelatedRecords relatedRecords) {

    Request request = relatedRecords.getRequest();
    Item item = request.getItem();
    NoticeEventType eventType = requestTypeToEventMap.getOrDefault(request.getRequestType(),
      NoticeEventType.UNKNOWN);
    PatronNoticeEvent requestCreatedEvent = createPatronNoticeEvent(request, eventType)
      .withItem(item);

    patronNoticeService.acceptNoticeEvent(requestCreatedEvent);
    Loan loan = request.getLoan();
    if (request.getRequestType() == RequestType.RECALL && loan != null) {
      sendNoticeOnItemRecalledEvent(loan);
      sendLogEvent(loan);
    }
    return Result.succeeded(relatedRecords);
  }

  @Override
  protected Result<Void> sendNoticeOnRequestCancelled(RequestAndRelatedRecords relatedRecords) {
    Request request = relatedRecords.getRequest();
    PatronNoticeEvent requestCancelledEvent = createPatronNoticeEvent(
      request, NoticeEventType.REQUEST_CANCELLATION).withItem(request.getItem());
    patronNoticeService.acceptNoticeEvent(requestCancelledEvent);

    return Result.succeeded(null);
  }
}
