package org.folio.circulation.domain;

import static org.folio.circulation.domain.notice.NoticeContextUtil.createRequestNoticeContext;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.support.Result;

public class UpdateRequestService {
  private final RequestRepository requestRepository;
  private final UpdateRequestQueue updateRequestQueue;
  private final ClosedRequestValidator closedRequestValidator;
  private final PatronNoticeService patronNoticeService;

  public UpdateRequestService(
    RequestRepository requestRepository,
    UpdateRequestQueue updateRequestQueue,
    ClosedRequestValidator closedRequestValidator, PatronNoticeService patronNoticeService) {

    this.requestRepository = requestRepository;
    this.updateRequestQueue = updateRequestQueue;
    this.closedRequestValidator = closedRequestValidator;
    this.patronNoticeService = patronNoticeService;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> replaceRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return closedRequestValidator.refuseWhenAlreadyClosed(requestAndRelatedRecords)
      .thenApply(r -> r.next(this::removeRequestQueuePositionWhenCancelled))
      .thenComposeAsync(r -> r.after(requestRepository::update))
      .thenComposeAsync(r -> r.after(updateRequestQueue::onCancellation))
      .thenApply(r -> r.next(this::sendNoticeOnRequestUpdated));
  }

  private Result<RequestAndRelatedRecords> removeRequestQueuePositionWhenCancelled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    //TODO: Extract to cancel method
    if(request.isCancelled()) {
      requestAndRelatedRecords.getRequestQueue().remove(request);
    }

    return succeeded(requestAndRelatedRecords);
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
