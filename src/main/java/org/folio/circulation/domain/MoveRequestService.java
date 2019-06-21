package org.folio.circulation.domain;

import static java.lang.String.format;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;

public class MoveRequestService {
  private final RequestRepository requestRepository;
  private final RequestQueueRepository requestQueueRepository;
  private final RequestPolicyRepository requestPolicyRepository;
  private final LoanRepository loanRepository;
  private final ItemRepository itemRepository;
  private final UpdateRequestQueue updateRequestQueue;
  private final UpdateItem updateItem;
  private final UpdateLoan updateLoan;
  private final UpdateLoanActionHistory updateLoanActionHistory;

  public MoveRequestService(RequestRepository requestRepository, RequestQueueRepository requestQueueRepository,
    RequestPolicyRepository requestPolicyRepository, UpdateRequestQueue updateRequestQueue, UpdateItem updateItem,
    UpdateLoanActionHistory updateLoanActionHistory, UpdateLoan updateLoan, LoanRepository loanRepository,
    ItemRepository itemRepository) {
    this.requestRepository = requestRepository;
    this.requestQueueRepository = requestQueueRepository;
    this.requestPolicyRepository = requestPolicyRepository;
    this.updateRequestQueue = updateRequestQueue;
    this.updateItem = updateItem;
    this.updateLoan = updateLoan;
    this.updateLoanActionHistory = updateLoanActionHistory;
    this.loanRepository = loanRepository;
    this.itemRepository = itemRepository;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> moveRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {
    final Item originalItem = requestAndRelatedRecords.getRequest().getItem();
    return of(() -> requestAndRelatedRecords)
      .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
      .after(this::lookupDestinationItem)
      .thenComposeAsync(r -> r.after(this::lookupDestinationItemRequestQueue))
      .thenApply(r -> r.map(MoveRequestService::applyMoveToRepresentation))
      .thenApply(r -> r.map(MoveRequestService::pagedRequestIfDestinationItemAvailable))
      .thenCompose(r -> r.after(this::updateRequest))
      .thenApply(r -> r.map(v -> useOriginalItemAndDestination(requestAndRelatedRecords, originalItem)))
      .thenCompose(r -> r.after(updateRequestQueue::onMoved));
  }
  
  private RequestAndRelatedRecords useOriginalItemAndDestination(
    RequestAndRelatedRecords requestAndRelatedRecords, Item originalItem) {
    String destinationItemId = requestAndRelatedRecords.getItemId();
    // NOTE: adding destinationItemId back to indicate moved
    return requestAndRelatedRecords.withItem(originalItem).withDestination(destinationItemId);
  }

  private static RequestAndRelatedRecords applyMoveToRepresentation(
    RequestAndRelatedRecords requestAndRelatedRecords) {
    requestAndRelatedRecords.withRequest(
      requestAndRelatedRecords.getRequest().applyMoveToRepresentation());
    return requestAndRelatedRecords;
  }
  
  private static RequestAndRelatedRecords pagedRequestIfDestinationItemAvailable(
    RequestAndRelatedRecords requestAndRelatedRecords) {
    Item item = requestAndRelatedRecords.getRequest().getItem();
    if (item.getStatus().equals(ItemStatus.AVAILABLE)) {
      requestAndRelatedRecords.withRequest(
        requestAndRelatedRecords.getRequest().changeType(RequestType.PAGE));
    }
    return requestAndRelatedRecords;
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> lookupDestinationItem(
    RequestAndRelatedRecords requestAndRelatedRecords) {
    final Request request = requestAndRelatedRecords.getRequest();
    final String destinationItemId = request.getDestinationItemId();
    return itemRepository.fetchById(destinationItemId)
      .thenApply(result -> result.map(requestAndRelatedRecords::withItem));
  }
  
  private CompletableFuture<Result<RequestAndRelatedRecords>> lookupDestinationItemRequestQueue(
    RequestAndRelatedRecords requestAndRelatedRecords) {
    final Request request = requestAndRelatedRecords.getRequest();
    final String destinationItemId = request.getDestinationItemId();
    return requestQueueRepository.get(destinationItemId)
      .thenApply(result -> result.map(requestAndRelatedRecords::withRequestQueue));
  }
  
  private CompletableFuture<Result<RequestAndRelatedRecords>> updateRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {
    return of(() -> requestAndRelatedRecords)
      .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
      .next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
      .next(RequestServiceUtility::refuseWhenItemIsNotValid)
      .next(RequestServiceUtility::refuseWhenUserHasAlreadyRequestedItem)
      .next(MoveRequestService::refuseWhenDestinationItemIsCheckedOutWithNoRecalls)
      .after(this::refuseWhenUserHasAlreadyBeenLoanedItem)
      .thenComposeAsync(r -> r.after(requestPolicyRepository::lookupRequestPolicy))
      .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled))
      .thenApply(r -> r.map(RequestServiceUtility::setRequestQueuePosition))
      .thenComposeAsync(r -> r.after(updateItem::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(updateLoan::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(requestRepository::update));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenUserHasAlreadyBeenLoanedItem(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    return loanRepository.findOpenLoanForRequest(request)
      .thenApply(loanResult -> loanResult.failWhen(
        loan -> of(() -> loan != null && loan.getUserId().equals(request.getUserId())),
        loan -> {
          Map<String, String> parameters = new HashMap<>();
          parameters.put("itemId", request.getItemId());
          parameters.put("userId", request.getUserId());
          parameters.put("loanId", loan.getId());

          String message = "This requester currently has this item on loan.";

          return singleValidationError(new ValidationError(message, parameters));
        })
        .map(loan -> requestAndRelatedRecords));
  }

  private static Result<RequestAndRelatedRecords> refuseWhenDestinationItemIsCheckedOutWithNoRecalls(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();    
    final RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();
    final RequestType requestType = request.getRequestType();

    boolean isRecall = requestType.equals(RequestType.RECALL);

    boolean hasRecallRequestInQueue = requestQueue.getRequests().stream()
      .filter(req -> req.getRequestType().equals(RequestType.RECALL))
      .collect(Collectors.toList()).size() > 0;

    if (isRecall && !hasRecallRequestInQueue) {
      return failedValidation(format("Cannot move recall request to item which has no recall requests"),
        REQUEST_TYPE, requestType.getValue());
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }
}
