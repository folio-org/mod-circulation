package org.folio.circulation.domain;

import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_UPDATED;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.results.Result;

public class UpdateRequestService {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
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

    log.debug("replaceRequest:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    Request updated = requestAndRelatedRecords.getRequest();

    return requestRepository.getById(updated.getId())
      .thenApply(originalRequest -> refuseWhenPatronCommentChanged(updated, originalRequest))
      .thenCompose(original -> original.after(request ->
        closedRequestValidator.refuseWhenAlreadyClosed(requestAndRelatedRecords)
        .thenApply(r -> r.next(this::removeRequestQueuePositionWhenCancelled))
        .thenComposeAsync(r -> r.after(requestRepository::update))
        .thenApplyAsync(r -> r.next(records ->
          requestNoticeSender.sendNoticeOnMediatedRequestCreated(request, records)))
        .thenComposeAsync(r -> r.after(updateRequestQueue::onCancellation))
        .thenComposeAsync(r -> r.after(updateItem::onRequestCreateOrUpdate))
        .thenApplyAsync(r -> r.map(p -> eventPublisher.publishLogRecordAsync(p, request, REQUEST_UPDATED)))
        .thenApply(r -> r.next(requestNoticeSender::sendNoticeOnRequestUpdated))));
  }

  private Result<Request> refuseWhenPatronCommentChanged(
    Request updated, Result<Request> originalRequestResult) {

    return originalRequestResult.failWhen(
      original -> succeeded(!Objects.equals(updated.getPatronComments(), original.getPatronComments())),
      original -> singleValidationError("Patron comments are not allowed to change",
        "existingPatronComments", original.getPatronComments())
    );
  }

  private Result<RequestAndRelatedRecords> removeRequestQueuePositionWhenCancelled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    if(request.isCancelled()) {
      log.info("removeRequestQueuePositionWhenCancelled:: request {} is cancelled, " +
        "removing from the request queue", request.getId());
      requestAndRelatedRecords.getRequestQueue().remove(request);
    }

    return succeeded(requestAndRelatedRecords);
  }
}
