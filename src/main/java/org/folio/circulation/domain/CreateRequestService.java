package org.folio.circulation.domain;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.Request.REQUEST_TYPE;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CreateRequestService {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
      RequestAndRelatedRecords requestAndRelatedRecords) throws InterruptedException {

    return completedFuture(refuseWhenItemDoesNotExist(requestAndRelatedRecords)
        .next(CreateRequestService::refuseWhenInvalidUserAndPatronGroup)
        .next(CreateRequestService::refuseWhenItemIsNotValid)
        .next(CreateRequestService::refuseWhenUserHasAlreadyRequestedItem))
            .thenApply(r-> refuseWhenUserHasAlreadyBeenLoanedItem(r, loanRepository))
            .thenComposeAsync(r -> r.after(requestPolicyRepository::lookupRequestPolicy))
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

  private Result<RequestAndRelatedRecords> refuseWhenUserHasAlreadyBeenLoanedItem(Result<RequestAndRelatedRecords> result,
      LoanRepository loanRepository) {

    RequestAndRelatedRecords requestAndRelatedRecords = result.value();

    if(requestAndRelatedRecords != null) {
      Request request = requestAndRelatedRecords.getRequest();
    
      Loan loan = null;
      try {
        loan = loanRepository
                .findOpenLoanForRequest(request)
                .get()
                .value();
      } catch (InterruptedException e) {
        log.error(e.getMessage(), e);
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        log.error(e.getMessage(), e);
        Map<String, String> parameters = new HashMap<>();
        String message = "The loan associated with this request could not be validated.";
        return failedValidation(new ValidationError(message, parameters));
      }
  
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
    } else {
      return result;
    }
  }
}
