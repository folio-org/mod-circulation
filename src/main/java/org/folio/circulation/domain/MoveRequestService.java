package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.of;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.domain.validation.RequestLoanValidator;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.support.Result;

public class MoveRequestService {
  private final RequestRepository requestRepository;
  private final RequestPolicyRepository requestPolicyRepository;
  private final UpdateRequestQueue updateRequestQueue;
  private final UpdateUponRequest updateUponRequest;
  private final MoveRequestProcessAdapter moveRequestProcessAdapter;
  private final RequestLoanValidator requestLoanValidator;
  private final RequestNoticeSender requestNoticeSender;

  public MoveRequestService(RequestRepository requestRepository, RequestPolicyRepository requestPolicyRepository,
      UpdateRequestQueue updateRequestQueue, UpdateUponRequest updateUponRequest,
      MoveRequestProcessAdapter moveRequestHelper, RequestLoanValidator requestLoanValidator,
      RequestNoticeSender requestNoticeSender) {
    this.requestRepository = requestRepository;
    this.requestPolicyRepository = requestPolicyRepository;
    this.updateRequestQueue = updateRequestQueue;
    this.updateUponRequest = updateUponRequest;
    this.moveRequestProcessAdapter = moveRequestHelper;
    this.requestLoanValidator = requestLoanValidator;
    this.requestNoticeSender = requestNoticeSender;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> moveRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return completedFuture(of(() -> requestAndRelatedRecords))
        .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::findDestinationItem))
        .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::getDestinationRequestQueue))
        .thenApply(r -> r.map(this::pagedRequestIfDestinationItemAvailable))
        .thenCompose(r -> r.after(this::validateUpdateRequest))
        .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::findSourceItem))
        .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::getSourceRequestQueue))
        .thenCompose(r -> r.after(updateRequestQueue::onMovedFrom))
        .thenCompose(r -> r.after(updateUponRequest.updateItem::onRequestQueueChanged))
        .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::findDestinationItem))
        .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::getDestinationRequestQueue))
        .thenCompose(r -> r.after(updateRequestQueue::onMovedTo))
        .thenComposeAsync(r -> r.after(updateUponRequest.updateItem::onRequestCreationOrMove))
        .thenComposeAsync(r -> r.after(updateUponRequest.updateLoanActionHistory::onRequestCreationOrMove))
        .thenComposeAsync(r -> r.after(updateUponRequest.updateLoan::onRequestCreationOrMove))
        .thenCompose(r -> r.after(requestRepository::update))
        .thenApply(r -> r.next(requestNoticeSender::sendNoticeOnRequestMoved));
  }

  private RequestAndRelatedRecords pagedRequestIfDestinationItemAvailable(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    Item item = requestAndRelatedRecords.getRequest().getItem();
    if (item.getStatus().equals(ItemStatus.AVAILABLE)) {
      return requestAndRelatedRecords.withRequestType(RequestType.PAGE);
    }
    return requestAndRelatedRecords;
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> validateUpdateRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return of(() -> requestAndRelatedRecords)
        .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
        .next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
        .next(RequestServiceUtility::refuseWhenItemIsNotValid)
        .next(RequestServiceUtility::refuseWhenUserHasAlreadyRequestedItem)
        .after(requestLoanValidator::refuseWhenUserHasAlreadyBeenLoanedItem)
        .thenComposeAsync(r -> r.after(requestPolicyRepository::lookupRequestPolicy))
        .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled));
  }
}
