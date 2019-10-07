package org.folio.circulation.domain;

import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.support.Result;

public class UpdateRequestService {
  private final RequestRepository requestRepository;
  private final UpdateRequestQueue updateRequestQueue;
  private final ClosedRequestValidator closedRequestValidator;
  private final RequestNoticeSender requestNoticeSender;

  private final UpdateItem updateItem;

  public UpdateRequestService(
    RequestRepository requestRepository,
    UpdateRequestQueue updateRequestQueue,
    ClosedRequestValidator closedRequestValidator,
    RequestNoticeSender requestNoticeSender,
    UpdateItem updateItem) {

    this.requestRepository = requestRepository;
    this.updateRequestQueue = updateRequestQueue;
    this.closedRequestValidator = closedRequestValidator;
    this.requestNoticeSender = requestNoticeSender;
    this.updateItem = updateItem;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> replaceRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return closedRequestValidator.refuseWhenAlreadyClosed(requestAndRelatedRecords)
      .thenApply(r -> r.next(RequestServiceUtility::refuseWhenUserIsInactive))
      .thenApply(r -> r.next(this::removeRequestQueuePositionWhenCancelled))
      .thenComposeAsync(r -> r.after(requestRepository::update))
      .thenComposeAsync(r -> r.after(updateRequestQueue::onCancellation))
      .thenComposeAsync(r -> r.after(updateItem::onRequestCreateOrUpdate))
      .thenApply(r -> r.next(requestNoticeSender::sendNoticeOnRequestUpdated));
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
}
