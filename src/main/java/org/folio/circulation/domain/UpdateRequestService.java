package org.folio.circulation.domain;

import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.support.HttpResult;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.HttpResult.succeeded;

public class UpdateRequestService {
  private final RequestRepository requestRepository;
  private final UpdateRequestQueue updateRequestQueue;
  private final ProxyRelationshipValidator proxyRelationshipValidator;
  private final ClosedRequestValidator closedRequestValidator;

  public UpdateRequestService(
    RequestRepository requestRepository,
    UpdateRequestQueue updateRequestQueue,
    ProxyRelationshipValidator proxyRelationshipValidator,
    ClosedRequestValidator closedRequestValidator) {

    this.requestRepository = requestRepository;
    this.updateRequestQueue = updateRequestQueue;
    this.proxyRelationshipValidator = proxyRelationshipValidator;
    this.closedRequestValidator = closedRequestValidator;
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> replaceRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return closedRequestValidator.refuseWhenAlreadyClosed(requestAndRelatedRecords)
      .thenApply(r -> r.next(this::removeRequestQueuePositionWhenCancelled))
      .thenComposeAsync(r -> r.after(requestRepository::update))
      .thenComposeAsync(r -> r.after(updateRequestQueue::onCancellation));
  }

  private HttpResult<RequestAndRelatedRecords> removeRequestQueuePositionWhenCancelled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    //TODO: Extract to cancel method
    if(request.isCancelled()) {
      requestAndRelatedRecords.getRequestQueue().remove(request);
    }

    return succeeded(requestAndRelatedRecords);
  }
}
