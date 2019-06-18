package org.folio.circulation.domain;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateRequestQueue {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final LoanRepository loanRepository;
  private final RequestQueueRepository requestQueueRepository;
  private final RequestRepository requestRepository;
  private final ServicePointRepository servicePointRepository;
  private final UpdateItem updateItem;
  private final UpdateLoanActionHistory updateLoanActionHistory;
  private final UpdateLoan updateLoan;

  public UpdateRequestQueue(
    LoanRepository loanRepository,
    RequestQueueRepository requestQueueRepository,
    RequestRepository requestRepository,
    ServicePointRepository servicePointRepository,
    UpdateItem updateItem,
    UpdateLoanActionHistory updateLoanActionHistory,
    UpdateLoan updateLoan) {

    this.loanRepository = loanRepository;
    this.requestQueueRepository = requestQueueRepository;
    this.requestRepository = requestRepository;
    this.servicePointRepository = servicePointRepository;
    this.updateItem = updateItem;
    this.updateLoanActionHistory = updateLoanActionHistory;
    this.updateLoan = updateLoan;
  }

  public static UpdateRequestQueue using(Clients clients, UpdateLoan updateLoan) {
    return new UpdateRequestQueue(
      new LoanRepository(clients),
      RequestQueueRepository.using(clients),
      RequestRepository.using(clients),
      new ServicePointRepository(clients),
      new UpdateItem(clients),
      new UpdateLoanActionHistory(clients),
      updateLoan);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> onCheckIn(
    LoanAndRelatedRecords relatedRecords) {

    //Do not attempt check in for open loan
    if(relatedRecords.getLoan().isOpen()) {
      return ofAsync(() -> relatedRecords);
    }

    final RequestQueue requestQueue = relatedRecords.getRequestQueue();

    return onCheckIn(requestQueue, relatedRecords.getLoan().getCheckInServicePointId())
      .thenApply(result -> result.map(relatedRecords::withRequestQueue));
  }

  public CompletableFuture<Result<RequestQueue>> onCheckIn(
    RequestQueue requestQueue, String checkInServicePointId) {

    if (requestQueue.hasOutstandingFulfillableRequests()) {
      Request firstRequest = requestQueue.getHighestPriorityFulfillableRequest();

      String requestPickupServicePointId = firstRequest.getPickupServicePointId();

      if (checkInServicePointId.equalsIgnoreCase(requestPickupServicePointId)) {
        firstRequest.changeStatus(RequestStatus.OPEN_AWAITING_PICKUP);

        if (firstRequest.getHoldShelfExpirationDate() == null) {
          return servicePointRepository.getServicePointById(requestPickupServicePointId)
              .thenApply(servicePointResult -> servicePointResult.map(firstRequest::withPickupServicePoint))
              .thenApply(requestResult -> requestResult.map(request -> {
                ServicePoint pickupServicePoint = request.getPickupServicePoint();
                TimePeriod holdShelfExpiryPeriod = pickupServicePoint.getHoldShelfExpiryPeriod();
                ZonedDateTime now = ZonedDateTime.now(ClockManager.getClockManager().getClock());
                ZonedDateTime holdShelfExpirationDate = holdShelfExpiryPeriod.getInterval().addTo(now, holdShelfExpiryPeriod.getDuration());
                // Need to use Joda time here since formatting/parsing using
                // java.time has issues with the ISO-8601 format FOLIO uses,
                // specifically: 2019-02-18T00:00:00.000+0000 cannot be parsed
                // due to a missing ':' in the offset. Parsing is possible if
                // the format is: 2019-02-18T00:00:00.000+00:00
                firstRequest.changeHoldShelfExpirationDate(new DateTime(holdShelfExpirationDate.toInstant().toEpochMilli(), DateTimeZone.UTC));

                return firstRequest;
              }))
              .thenComposeAsync(r -> r.after(requestRepository::update))
              .thenApply(r -> r.map(v -> requestQueue));
        }
      } else {
        firstRequest.changeStatus(RequestStatus.OPEN_IN_TRANSIT);
        firstRequest.removeHoldShelfExpirationDate();
      }

      return requestRepository.update(firstRequest)
        .thenApply(result -> result.map(v -> requestQueue));

    } else {
      return completedFuture(succeeded(requestQueue));
    }
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> onCheckOut(
    LoanAndRelatedRecords relatedRecords) {

    return onCheckOut(relatedRecords.getRequestQueue())
      .thenApply(result -> result.map(relatedRecords::withRequestQueue));
  }

  private CompletableFuture<Result<RequestQueue>> onCheckOut(RequestQueue requestQueue) {
    if (requestQueue.hasOutstandingFulfillableRequests()) {
      Request firstRequest = requestQueue.getHighestPriorityFulfillableRequest();

      log.info("Closing request '{}'", firstRequest.getId());
      firstRequest.changeStatus(RequestStatus.CLOSED_FILLED);

      log.info("Removing request '{}' from queue", firstRequest.getId());
      requestQueue.remove(firstRequest);

      return requestRepository.update(firstRequest)
        .thenComposeAsync(r -> r.after(v ->
          requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)));

    } else {
      return completedFuture(succeeded(requestQueue));
    }
  }

  public CompletableFuture<Result<MoveRequestRecords>> onMove(
    MoveRequestRecords moveRequestRecords) {

    return of(() -> moveRequestRecords)
      .next(this::refuseWhenItemDoesNotExist)
      .next(UpdateRequestQueue::refuseWhenInvalidUserAndPatronGroup)
      .next(UpdateRequestQueue::refuseWhenItemIsNotValid)
      .next(UpdateRequestQueue::refuseWhenUserHasAlreadyRequestedItem)
      .after(this::refuseWhenUserHasAlreadyBeenLoanedItem)
      .thenApply(r -> r.next(UpdateRequestQueue::refuseWhenRequestCannotBeFulfilled))
      .thenApply(this::updateRequestItem)
      .thenApply(this::updateRequestStatus)
      .thenApply(this::transferRecord)
      .thenComposeAsync(r -> r.after(updateItem::onRequestUpdate))
      .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestUpdate))
      .thenComposeAsync(r -> r.after(updateLoan::onRequestUpdate))
      .thenComposeAsync(r -> r.after(requestRepository::update));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> onCancellation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    if(requestAndRelatedRecords.getRequest().isCancelled()) {
      return requestQueueRepository.updateRequestsWithChangedPositions(
        requestAndRelatedRecords.getRequestQueue())
        .thenApply(r -> r.map(requestAndRelatedRecords::withRequestQueue));
    }
    else {
      return completedFuture(succeeded(requestAndRelatedRecords));
    }
  }

  public CompletableFuture<Result<Request>> onDeletion(Request request) {
    return requestQueueRepository.get(request.getItemId())
      .thenApply(r -> r.map(requestQueue -> {
        requestQueue.remove(request);
        return requestQueue;
      }))
      .thenComposeAsync(r -> r.after(
        requestQueueRepository::updateRequestsWithChangedPositions))
      .thenApply(r -> r.map(requestQueue -> request));
  }

  private Result<MoveRequestRecords> refuseWhenItemDoesNotExist(
      MoveRequestRecords moveRequestRecords) {

      if (moveRequestRecords.getRequest().getItem().isNotFound()) {
        return failedValidation("Item does not exist", "itemId", moveRequestRecords.getRequest().getItemId());
      } else {
        System.out.println("\n\n\nrefuse exist: " + moveRequestRecords.getRequest() + "\n\n\n");
        return succeeded(moveRequestRecords);
      }
    }

  private static Result<MoveRequestRecords> refuseWhenInvalidUserAndPatronGroup(
      MoveRequestRecords moveRequestRecords) {

    Request request = moveRequestRecords.getRequest();
    User requester = request.getRequester();

    // TODO: Investigate whether the parameter used here is correct
    // Should it be the userId for both of these failures?
    if (requester == null) {
      return failedValidation("A valid user and patron group are required. User is null", "userId", null);

    } else if (requester.getPatronGroupId() == null) {
      return failedValidation("A valid patron group is required. PatronGroup ID is null", "PatronGroupId", null);
    } else {
      System.out.println("\n\n\nrefuse patron group: " + moveRequestRecords.getRequest() + "\n\n\n");
      return succeeded(moveRequestRecords);
    }
  }

  private static Result<MoveRequestRecords> refuseWhenItemIsNotValid(
    MoveRequestRecords moveRequestRecords) {

    Request request = moveRequestRecords.getRequest();

    if (!request.allowedForItem()) {
      return failureDisallowedForRequestType(request.getRequestType());
    } else {
      System.out.println("\n\n\nrefuse invalid: " + moveRequestRecords.getRequest() + "\n\n\n");
      return succeeded(moveRequestRecords);
    }
  }

  private static ResponseWritableResult<MoveRequestRecords> failureDisallowedForRequestType(
    RequestType requestType) {

    final String requestTypeName = requestType.getValue();

    return failedValidation(format("%s requests are not allowed for this patron and item combination", requestTypeName),
      REQUEST_TYPE, requestTypeName);
  }

  public static Result<MoveRequestRecords> refuseWhenUserHasAlreadyRequestedItem(
      MoveRequestRecords moveRequestRecords) {

    Optional<Request> requestOptional = moveRequestRecords.getDestinationRequestQueue().getRequests().stream()
      .filter(it -> isTheSameRequester(moveRequestRecords, it) && it.isOpen()).findFirst();
    System.out.println("\n\n\nrequests on queue: " + moveRequestRecords.getDestinationRequestQueue().getRequests().stream().findFirst().get().getItemId() + "\n\n\n");

    if (requestOptional.isPresent()) {
      Map<String, String> parameters = new HashMap<>();
      parameters.put("requesterId", moveRequestRecords.getRequest().getUserId());
      parameters.put("itemId", moveRequestRecords.getRequest().getItemId());
      parameters.put("requestId", requestOptional.get().getId());
      String message = "This requester already has an open request for this item";
      System.out.println("\n\n\n found same requester: " + requestOptional.get().getId() + "\n\n\n");
      return failedValidation(new ValidationError(message, parameters));
    } else {
      System.out.println("\n\n\n refuse has item" + moveRequestRecords.getRequest() + "\n\n\n");
      return of(() -> moveRequestRecords);
    }
  }

  private static boolean isTheSameRequester(MoveRequestRecords it, Request that) {
    return Objects.equals(it.getRequest().getUserId(), that.getUserId());
  }

  private CompletableFuture<Result<MoveRequestRecords>> refuseWhenUserHasAlreadyBeenLoanedItem(
    MoveRequestRecords moveRequestRecords) {

    final Request request = moveRequestRecords.getRequest();

    return loanRepository.findOpenLoanForRequest(request)
      .thenApply(loanResult -> loanResult.failWhen(
        loan -> of(() -> loan != null && loan.getUserId().equals(request.getUserId())),
        loan -> {
          Map<String, String> parameters = new HashMap<>();
          parameters.put("itemId", request.getItemId());
          parameters.put("userId", request.getUserId());
          parameters.put("loanId", loan.getId());

          String message = "This requester currently has this item on loan.";
          System.out.println("\n\n\nrefuse already loaned: " + moveRequestRecords.getRequest() + "\n\n\n");
          return singleValidationError(new ValidationError(message, parameters));
        })
        .map(loan -> moveRequestRecords));
  }

  private static Result<MoveRequestRecords> refuseWhenRequestCannotBeFulfilled(
      MoveRequestRecords moveRequestRecords) {

    RequestPolicy requestPolicy = moveRequestRecords.getRequestPolicy();
    RequestType requestType = moveRequestRecords.getRequest().getRequestType();

    if (!requestPolicy.allowsType(requestType)) {
      return failureDisallowedForRequestType(requestType);
    } else {
      System.out.println("\n\n\nrefuse unfulfilled: " + moveRequestRecords.getRequest() + "\n\n\n");
      return succeeded(moveRequestRecords);
    }
  }

  private Result<MoveRequestRecords> updateRequestItem(
    Result<MoveRequestRecords> moveRequestRecords) {

    Item destinationItem = moveRequestRecords.value().getDestinationItem();
    Request request = moveRequestRecords.value().getRequest();
    Result<Request> updatedRequest = Result.of(() -> request.withItem(destinationItem).changeItem(destinationItem));
    return Result.combine(moveRequestRecords, updatedRequest,
      MoveRequestRecords::withRequest);
  }

  private Result<MoveRequestRecords> updateRequestStatus(
    Result<MoveRequestRecords> moveRequestRecords) {

    Request request = moveRequestRecords.value().getRequest();
    RequestStatus status = RequestStatus.from(calculateStatus(moveRequestRecords.value()));
    request.changeStatus(status);

    Result<Request> updatedRequest = Result.of(() -> request);
    return Result.combine(moveRequestRecords, updatedRequest,
        MoveRequestRecords::withRequest);
  }

  private String calculateStatus(MoveRequestRecords moveRequestRecords) {
//    TODO: implement logic
    return "Open - Not yet filled";
  }

  private Result<MoveRequestRecords> transferRecord(
      Result<MoveRequestRecords> moveRequestRecords) {
    
    MoveRequestRecords records = moveRequestRecords.value();
    records.transferRequest(records.getRequest());

    
    System.out.println("\n\n\nreplace" + moveRequestRecords.value().getRequest() + "\n\n\n");
      return succeeded(records);
    }
}
