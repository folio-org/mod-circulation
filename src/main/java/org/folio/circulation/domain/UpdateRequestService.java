package org.folio.circulation.domain;

import static org.folio.circulation.domain.representations.logs.RequestUpdateLogEventMapper.mapToRequestMoveLogEventJson;
import static org.folio.circulation.domain.representations.logs.RequestUpdateLogEventMapper.mapToRequestUpdateLogEventJson;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.results.Result;

public class UpdateRequestService {
  private final RequestRepository requestRepository;
  private final UpdateRequestQueue updateRequestQueue;
  private final ClosedRequestValidator closedRequestValidator;
  private final RequestNoticeSender requestNoticeSender;
  private final UpdateItem updateItem;
  private final EventPublisher eventPublisher;

  public UpdateRequestService(RequestRepository requestRepository,
    UpdateRequestQueue updateRequestQueue, ClosedRequestValidator closedRequestValidator,
    RequestNoticeSender requestNoticeSender, UpdateItem updateItem, EventPublisher eventPublisher) {

    this.requestRepository = requestRepository;
    this.updateRequestQueue = updateRequestQueue;
    this.closedRequestValidator = closedRequestValidator;
    this.requestNoticeSender = requestNoticeSender;
    this.updateItem = updateItem;
    this.eventPublisher = eventPublisher;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> replaceRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request updated = requestAndRelatedRecords.getRequest();

    return requestRepository.getById(updated.getId())
      .thenCompose(original -> closedRequestValidator.refuseWhenAlreadyClosed(requestAndRelatedRecords)
        .thenApply(r -> r.next(this::removeRequestQueuePositionWhenCancelled))
        .thenComposeAsync(r -> r.after(requestRepository::update))
        .thenComposeAsync(r -> r.after(updateRequestQueue::onCancellation))
        .thenComposeAsync(r -> r.after(updateItem::onRequestCreateOrUpdate))
        .thenApplyAsync(r -> r.next(p -> {
          CompletableFuture.runAsync(() -> requestRepository.getById(updated.getId())
            .thenComposeAsync(v -> v.after(s -> eventPublisher
              .publishLogRecordEvent(mapToRequestUpdateLogEventJson(new UpdatedRequestPair(original.value(), s))))));
          return requestNoticeSender.sendNoticeOnRequestUpdated(p);
        })));
  }

  private Result<RequestAndRelatedRecords> removeRequestQueuePositionWhenCancelled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    if(request.isCancelled()) {
      requestAndRelatedRecords.getRequestQueue().remove(request);
    }

    return succeeded(requestAndRelatedRecords);
  }
}
