package org.folio.circulation.domain;

import static java.lang.String.format;
import static org.folio.circulation.domain.Request.REQUEST_TYPE;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;

import io.vertx.core.impl.FailedFuture;

public class CreateRequestService {

  private final RequestRepository requestRepository;
  private final UpdateItem updateItem;
  private final UpdateLoanActionHistory updateLoanActionHistory;
  private final RequestPolicyRepository requestPolicyRepository;
  private final LoanRepository loanRepository;
  private final UpdateLoan updateLoan;

  public CreateRequestService(RequestRepository requestRepository, UpdateItem updateItem,
    UpdateLoanActionHistory updateLoanActionHistory, UpdateLoan updateLoan,
    RequestPolicyRepository requestPolicyRepository, LoanRepository loanRepository) {

    this.requestRepository = requestRepository;
    this.updateItem = updateItem;
    this.updateLoanActionHistory = updateLoanActionHistory;
    this.updateLoan = updateLoan;
    this.requestPolicyRepository = requestPolicyRepository;
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> createRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return findOpenLoanWithRequest(requestAndRelatedRecords, loanRepository)
      .thenApply(loanResult ->
         refuseWhenLoanRequestFails(requestAndRelatedRecords, loanResult) 
           .next(r->refuseWhenUserHasAlreadyBeenLoanedItem(requestAndRelatedRecords, loanResult))
           .next(CreateRequestService::refuseWhenItemDoesNotExist)
           .next(CreateRequestService::refuseWhenInvalidUserAndPatronGroup)
           .next(CreateRequestService::refuseWhenItemIsNotValid)
           .next(CreateRequestService::refuseWhenUserHasAlreadyRequestedItem)
       ).thenComposeAsync(r -> r.after(requestPolicyRepository::lookupRequestPolicy))
        .thenApply(r -> r.next(CreateRequestService::refuseWhenRequestCannotBeFulfilled))
        .thenApply(r -> r.map(CreateRequestService::setRequestQueuePosition))
        .thenComposeAsync(r -> r.after(updateItem::onRequestCreation))
        .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestCreation))
        .thenComposeAsync(r -> r.after(updateLoan::onRequestCreation))
        .thenComposeAsync(r -> r.after(requestRepository::create));
  }

  private static RequestAndRelatedRecords setRequestQueuePosition(RequestAndRelatedRecords requestAndRelatedRecords) {
    // TODO: Extract to method to add to queue
    requestAndRelatedRecords.withRequest(requestAndRelatedRecords.getRequest()
        .changePosition(requestAndRelatedRecords.getRequestQueue().nextAvailablePosition()));

    return requestAndRelatedRecords;
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

  public static Result<RequestAndRelatedRecords> refuseWhenUserHasAlreadyRequestedItem(
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
      return Result.of(() -> request);
    }
  }

  private static boolean isTheSameRequester(RequestAndRelatedRecords it, Request that) {
    return Objects.equals(it.getUserId(), that.getUserId());
  }
  
  private static CompletableFuture<Result<Loan>> findOpenLoanWithRequest(RequestAndRelatedRecords requestAndRelatedRecords,
    LoanRepository loanRepository) {
    Request request = requestAndRelatedRecords.getRequest();
    return loanRepository.findOpenLoanForRequest(request);
  }

  private static Result<RequestAndRelatedRecords> refuseWhenLoanRequestFails(RequestAndRelatedRecords requestAndRelatedRecords,
    Result<Loan> loanResult) {
    if (loanResult.failed()) {
      return Result.failed(loanResult.cause());
    } else {
      return Result.of(() -> requestAndRelatedRecords);
    }
  }

  private static Result<RequestAndRelatedRecords> refuseWhenUserHasAlreadyBeenLoanedItem(RequestAndRelatedRecords requestAndRelatedRecords,
    Result<Loan> loanResult) {

    Request request = requestAndRelatedRecords.getRequest();

    Loan loan = loanResult.value();

    String userId = request.getProxyUserId() == null ? request.getUserId() : request.getProxyUserId();

    if (loan != null && loan.getUserId().equals(userId)) {
      Map<String, String> parameters = new HashMap<>();
      parameters.put("userId", userId);
      parameters.put("loanId", loan.getId());
      String message = "This requester currently has this item on loan.";
      return failedValidation(new ValidationError(message, parameters));
    } else {
      return Result.of(() -> requestAndRelatedRecords);
    }

  }

}
