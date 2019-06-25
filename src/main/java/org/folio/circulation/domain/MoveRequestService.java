package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.domain.validation.RequestLoanValidator;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

public class MoveRequestService {
  private final RequestRepository requestRepository;
  private final RequestQueueRepository requestQueueRepository;
  private final RequestPolicyRepository requestPolicyRepository;
  private final ItemRepository itemRepository;
  private final UpdateRequestQueue updateRequestQueue;
  private final UpdateUponRequest updateUponRequest;
  private final RequestLoanValidator requestLoanValidator;

  public MoveRequestService(RequestRepository requestRepository, RequestQueueRepository requestQueueRepository,
      RequestPolicyRepository requestPolicyRepository, ItemRepository itemRepository,
      UpdateRequestQueue updateRequestQueue, UpdateUponRequest updateUponRequest,
      RequestLoanValidator requestLoanValidator) {
    this.requestRepository = requestRepository;
    this.requestQueueRepository = requestQueueRepository;
    this.requestPolicyRepository = requestPolicyRepository;
    this.itemRepository = itemRepository;
    this.updateRequestQueue = updateRequestQueue;
    this.updateUponRequest = updateUponRequest;
    this.requestLoanValidator = requestLoanValidator;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> moveRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return completedFuture(of(() -> requestAndRelatedRecords)).thenComposeAsync(r -> r.after(this::withDestinationItem))
        .thenComposeAsync(r -> r.after(this::withDestinationItemRequestQueue))
        .thenApply(r -> r.map(MoveRequestService::pagedRequestIfDestinationItemAvailable))
        .thenCompose(r -> r.after(this::updateRequest)).thenComposeAsync(r -> r.after(this::withOriginalItem))
        .thenComposeAsync(r -> r.after(this::withOriginalItemRequestQueue))
        .thenCompose(r -> r.after(updateRequestQueue::onMoved))
        .thenComposeAsync(r -> r.after(this::withDestinationItem))
        .thenComposeAsync(r -> r.after(this::withDestinationItemRequestQueue));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> withDestinationItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return itemRepository.fetchById(requestAndRelatedRecords.getDestinationItemId())
        .thenApply(result -> result.map(requestAndRelatedRecords::withItem));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> withDestinationItemRequestQueue(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return requestQueueRepository.get(requestAndRelatedRecords.getDestinationItemId())
        .thenApply(result -> result.map(requestAndRelatedRecords::withRequestQueue));
  }

  private static RequestAndRelatedRecords pagedRequestIfDestinationItemAvailable(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    Item item = requestAndRelatedRecords.getRequest().getItem();
    if (item.getStatus().equals(ItemStatus.AVAILABLE)) {
      return requestAndRelatedRecords.withRequestType(RequestType.PAGE);
    }
    return requestAndRelatedRecords;
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> withOriginalItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return itemRepository.fetchById(requestAndRelatedRecords.getOriginalItemId())
        .thenApply(result -> result.map(requestAndRelatedRecords::withItem));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> withOriginalItemRequestQueue(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return requestQueueRepository.get(requestAndRelatedRecords.getOriginalItemId())
        .thenApply(result -> result.map(requestAndRelatedRecords::withRequestQueue));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> updateRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return of(() -> requestAndRelatedRecords).next(RequestServiceUtility::refuseWhenItemDoesNotExist)
        .next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
        .next(RequestServiceUtility::refuseWhenItemIsNotValid)
        .next(RequestServiceUtility::refuseWhenUserHasAlreadyRequestedItem)
        .next(MoveRequestService::refuseWhenMovingRecallRequestToCheckedOutDestinationItemIdWithNoRecalls)
        .after(requestLoanValidator::refuseWhenUserHasAlreadyBeenLoanedItem)
        .thenComposeAsync(r -> r.after(requestPolicyRepository::lookupRequestPolicy))
        .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled))
        .thenApply(r -> r.map(RequestServiceUtility::setRequestQueuePosition))
        .thenComposeAsync(r -> r.after(updateUponRequest.updateItem::onRequestCreationOrMove))
        .thenComposeAsync(r -> r.after(updateUponRequest.updateLoanActionHistory::onRequestCreationOrMove))
        .thenComposeAsync(r -> r.after(updateUponRequest.updateLoan::onRequestCreationOrMove))
        .thenComposeAsync(r -> r.after(requestRepository::update));
  }

  private static Result<RequestAndRelatedRecords> refuseWhenMovingRecallRequestToCheckedOutDestinationItemIdWithNoRecalls(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();
    final RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();
    final RequestType requestType = request.getRequestType();

    boolean isRecall = requestType.equals(RequestType.RECALL);

    boolean noRecallRequestsInQueue = requestQueue.getRequests().stream()
        .filter(req -> req.getRequestType().equals(RequestType.RECALL)).collect(Collectors.toList()).isEmpty();

    if (isRecall && noRecallRequestsInQueue) {
      return failedValidation("Recalls can't be moved to checked out items that have not been previously recalled.",
          REQUEST_TYPE, requestType.getValue());
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }
}
