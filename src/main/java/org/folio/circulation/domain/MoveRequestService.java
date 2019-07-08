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
  private final MoveRequestHelper moveRequestHelper;
  private final RequestLoanValidator requestLoanValidator;
  private final RequestNoticeSender requestNoticeSender;

  public MoveRequestService(RequestRepository requestRepository, RequestPolicyRepository requestPolicyRepository,
      UpdateRequestQueue updateRequestQueue, UpdateUponRequest updateUponRequest,
      MoveRequestHelper moveRequestHelper, RequestLoanValidator requestLoanValidator,
      RequestNoticeSender requestNoticeSender) {
    this.requestRepository = requestRepository;
    this.requestPolicyRepository = requestPolicyRepository;
    this.updateRequestQueue = updateRequestQueue;
    this.updateUponRequest = updateUponRequest;
    this.moveRequestHelper = moveRequestHelper;
    this.requestLoanValidator = requestLoanValidator;
    this.requestNoticeSender = requestNoticeSender;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> moveRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return completedFuture(of(() -> requestAndRelatedRecords))
        .thenComposeAsync(r -> r.after(moveRequestHelper::withDestinationItem))
        .thenComposeAsync(r -> r.after(moveRequestHelper::withDestinationItemRequestQueue))
        .thenApply(r -> r.map(this::pagedRequestIfDestinationItemAvailable))
        .thenCompose(r -> r.after(this::updateRequest))
        .thenComposeAsync(r -> r.after(moveRequestHelper::withOriginalItem))
        .thenComposeAsync(r -> r.after(moveRequestHelper::withOriginalItemRequestQueue))
        .thenCompose(r -> r.after(updateRequestQueue::onMoved))
        .thenComposeAsync(r -> r.after(moveRequestHelper::withDestinationItem))
        .thenComposeAsync(r -> r.after(moveRequestHelper::withDestinationItemRequestQueue));
  }

  private RequestAndRelatedRecords pagedRequestIfDestinationItemAvailable(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    Item item = requestAndRelatedRecords.getRequest().getItem();
    if (item.getStatus().equals(ItemStatus.AVAILABLE)) {
      return requestAndRelatedRecords.withRequestType(RequestType.PAGE);
    }
    return requestAndRelatedRecords;
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> updateRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return of(() -> requestAndRelatedRecords)
        .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
        .next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
        .next(RequestServiceUtility::refuseWhenItemIsNotValid)
        .next(RequestServiceUtility::refuseWhenUserHasAlreadyRequestedItem)
        .after(requestLoanValidator::refuseWhenUserHasAlreadyBeenLoanedItem)
        .thenComposeAsync(r -> r.after(requestPolicyRepository::lookupRequestPolicy))
        .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled))
        .thenApply(r -> r.map(RequestServiceUtility::setRequestQueuePosition))
        .thenComposeAsync(r -> r.after(updateUponRequest.updateItem::onRequestCreationOrMove))
        .thenComposeAsync(r -> r.after(updateUponRequest.updateLoanActionHistory::onRequestCreationOrMove))
        .thenComposeAsync(r -> r.after(updateUponRequest.updateLoan::onRequestCreationOrMove))
        .thenComposeAsync(r -> r.after(requestRepository::update))
        .thenApply(r -> r.next(requestNoticeSender::sendNoticeOnRequestMoved));
  }
}
