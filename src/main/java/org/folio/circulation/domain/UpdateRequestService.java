package org.folio.circulation.domain;

import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.support.Result;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.Result.succeeded;

public class UpdateRequestService {
  private final RequestRepository requestRepository;
  private final UpdateRequestQueue updateRequestQueue;
  private final ClosedRequestValidator closedRequestValidator;

  public UpdateRequestService(
    RequestRepository requestRepository,
    UpdateRequestQueue updateRequestQueue,
    ClosedRequestValidator closedRequestValidator) {

    this.requestRepository = requestRepository;
    this.updateRequestQueue = updateRequestQueue;
    this.closedRequestValidator = closedRequestValidator;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> replaceRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return closedRequestValidator.refuseWhenAlreadyClosed(requestAndRelatedRecords)
      .thenApply(r -> r.next(this::removeRequestQueuePositionWhenCancelled))
      .thenComposeAsync(r -> r.after(requestRepository::update))
      .thenComposeAsync(r -> r.after(updateRequestQueue::onCancellation));
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
