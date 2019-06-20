package org.folio.circulation.domain;

import static java.lang.String.format;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.ResponseWritableResult;
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
    return of(() -> requestAndRelatedRecords)
      .next(MoveRequestService::refuseWhenItemDoesNotExist)
      .after(updateRequestQueue::onMoveFrom)
      .thenComposeAsync(r -> r.after(this::lookupDestinationItem))
      .thenComposeAsync(r -> r.after(this::lookupDestinationItemRequestQueue))
      .thenCompose(r -> r.after(updateRequestQueue::onMoveTo))
      .thenApply(r -> r.map(MoveRequestService::applyMoveToRepresentation))
      .thenCompose(r -> r.after(this::updateRequest));
  }
  
  private static RequestAndRelatedRecords applyMoveToRepresentation(
    RequestAndRelatedRecords requestAndRelatedRecords) {
    requestAndRelatedRecords.withRequest(
      requestAndRelatedRecords.getRequest().applyMoveToRepresentation());
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
      .next(MoveRequestService::refuseWhenItemDoesNotExist)
      .next(MoveRequestService::refuseWhenInvalidUserAndPatronGroup)
      .next(MoveRequestService::refuseWhenItemIsNotValid)
      .next(MoveRequestService::refuseWhenUserHasAlreadyRequestedItem)
      .after(this::refuseWhenUserHasAlreadyBeenLoanedItem)
      .thenComposeAsync(r -> r.after(requestPolicyRepository::lookupRequestPolicy))
      .thenApply(r -> r.next(MoveRequestService::refuseWhenRequestCannotBeFulfilled))
      .thenComposeAsync(r -> r.after(updateItem::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(updateLoan::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(requestRepository::update));
  }

  private static Result<RequestAndRelatedRecords> refuseWhenItemDoesNotExist(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    if (requestAndRelatedRecords.getRequest().getItem().isNotFound()) {
      return failedValidation("Item does not exist", "itemId", requestAndRelatedRecords.getRequest().getItemId());
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  private static Result<RequestAndRelatedRecords> refuseWhenRequestCannotBeFulfilled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    RequestPolicy requestPolicy = requestAndRelatedRecords.getRequestPolicy();
    RequestType requestType = requestAndRelatedRecords.getRequest().getRequestType();

    if (!requestPolicy.allowsType(requestType)) {
      return failureDisallowedForRequestType(requestType);
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  private static Result<RequestAndRelatedRecords> refuseWhenItemIsNotValid(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();

    if (!request.allowedForItem()) {
      return failureDisallowedForRequestType(request.getRequestType());
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  private static ResponseWritableResult<RequestAndRelatedRecords> failureDisallowedForRequestType(
    RequestType requestType) {

    final String requestTypeName = requestType.getValue();

    return failedValidation(format("%s requests are not allowed for this patron and item combination", requestTypeName),
      REQUEST_TYPE, requestTypeName);
  }

  private static Result<RequestAndRelatedRecords> refuseWhenInvalidUserAndPatronGroup(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();
    User requester = request.getRequester();

    // TODO: Investigate whether the parameter used here is correct
    // Should it be the userId for both of these failures?
    if (requester == null) {
      return failedValidation("A valid user and patron group are required. User is null", "userId", null);

    } else if (requester.getPatronGroupId() == null) {
      return failedValidation("A valid patron group is required. PatronGroup ID is null", "PatronGroupId", null);
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  private static Result<RequestAndRelatedRecords> refuseWhenUserHasAlreadyRequestedItem(
    RequestAndRelatedRecords request) {

    Optional<Request> requestOptional = request.getRequestQueue().getRequests().stream()
      .filter(it -> isTheSameRequester(request, it) && it.isOpen()).findFirst();

    if (requestOptional.isPresent()) {
      Map<String, String> parameters = new HashMap<>();
      parameters.put("requesterId", request.getRequest().getUserId());
      parameters.put("itemId", request.getRequest().getItemId());
      parameters.put("requestId", requestOptional.get().getId());
      String message = "This requester already has an open request for this item";
      return failedValidation(new ValidationError(message, parameters));
    } else {
      return of(() -> request);
    }
  }

  private static boolean isTheSameRequester(RequestAndRelatedRecords it, Request that) {
    return Objects.equals(it.getUserId(), that.getUserId());
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
}
