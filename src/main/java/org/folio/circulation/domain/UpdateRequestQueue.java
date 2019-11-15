package org.folio.circulation.domain;

import static java.util.Comparator.comparingInt;
import static java.util.concurrent.CompletableFuture.completedFuture;

import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.folio.circulation.resources.context.ReorderRequestContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.Result;

public class UpdateRequestQueue {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final RequestQueueRepository requestQueueRepository;
  private final RequestRepository requestRepository;
  private final ServicePointRepository servicePointRepository;

  public UpdateRequestQueue(
    RequestQueueRepository requestQueueRepository,
    RequestRepository requestRepository,
    ServicePointRepository servicePointRepository) {
    this.requestQueueRepository = requestQueueRepository;
    this.requestRepository = requestRepository;
    this.servicePointRepository = servicePointRepository;
  }

  public static UpdateRequestQueue using(Clients clients) {
    return new UpdateRequestQueue(
      RequestQueueRepository.using(clients),
      RequestRepository.using(clients),
      new ServicePointRepository(clients));
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
      return updateOutstandingRequestOnCheckIn(requestQueue, checkInServicePointId);
    } else {
      return completedFuture(succeeded(requestQueue));
    }
  }

  private CompletableFuture<Result<RequestQueue>> updateOutstandingRequestOnCheckIn(
    RequestQueue requestQueue, String checkInServicePointId) {

    Request requestBeingFulfilled = requestQueue.getHighestPriorityFulfillableRequest();

    CompletableFuture<Result<Request>> updatedReq;

    switch (requestBeingFulfilled.getFulfilmentPreference()) {
      case HOLD_SHELF:
        if (checkInServicePointId.equalsIgnoreCase(requestBeingFulfilled.getPickupServicePointId())) {
          updatedReq = awaitPickup(requestBeingFulfilled);
        } else {
          updatedReq = putInTransit(requestBeingFulfilled);
        }

        break;
      case DELIVERY:
        updatedReq = awaitDelivery(requestBeingFulfilled);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " +
          requestBeingFulfilled.getFulfilmentPreference());
    }

    return updatedReq
      .thenComposeAsync(r -> r.after(requestRepository::update))
      .thenApply(result -> result.map(v -> requestQueue));
  }

  private CompletableFuture<Result<Request>> awaitPickup(Request request) {
    request.changeStatus(RequestStatus.OPEN_AWAITING_PICKUP);

    if (request.getHoldShelfExpirationDate() == null) {
      String pickupServicePointId = request.getPickupServicePointId();

      return servicePointRepository.getServicePointById(pickupServicePointId)
        .thenApply(servicePointResult -> servicePointResult.map(request::withPickupServicePoint))
        .thenApply(requestResult -> requestResult.map(this::populateHoldShelfExpirationDate));
    } else {
      return completedFuture(succeeded(request));
    }
  }

  private CompletableFuture<Result<Request>> putInTransit(Request request) {
    request.changeStatus(RequestStatus.OPEN_IN_TRANSIT);
    request.removeHoldShelfExpirationDate();

    return completedFuture(succeeded(request));
  }

  private CompletableFuture<Result<Request>> awaitDelivery(Request request) {
    request.changeStatus(RequestStatus.OPEN_AWAITING_DELIVERY);
    request.removeHoldShelfExpirationDate();

    return completedFuture(succeeded(request));
  }

  private Request populateHoldShelfExpirationDate(Request request) {
    ServicePoint pickupServicePoint = request.getPickupServicePoint();

    TimePeriod holdShelfExpiryPeriod = pickupServicePoint.getHoldShelfExpiryPeriod();

    ZonedDateTime now = ZonedDateTime.now(ClockManager.getClockManager().getClock());
    ZonedDateTime holdShelfExpirationDate = holdShelfExpiryPeriod.getInterval().addTo(now, holdShelfExpiryPeriod.getDuration());
    // Need to use Joda time here since formatting/parsing using
    // java.time has issues with the ISO-8601 format FOLIO uses,
    // specifically: 2019-02-18T00:00:00.000+0000 cannot be parsed
    // due to a missing ':' in the offset. Parsing is possible if
    // the format is: 2019-02-18T00:00:00.000+00:00
    request.changeHoldShelfExpirationDate(new DateTime(holdShelfExpirationDate.toInstant().toEpochMilli(), DateTimeZone.UTC));

    return request;
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

  CompletableFuture<Result<RequestAndRelatedRecords>> onCreate(
    RequestAndRelatedRecords requestAndRelatedRecords) {
    final Request request = requestAndRelatedRecords.getRequest();
    final RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();
    requestQueue.add(request);
    return requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)
        .thenApply(r -> r.map(requestAndRelatedRecords::withRequestQueue));
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

  CompletableFuture<Result<RequestAndRelatedRecords>> onMovedFrom(
    RequestAndRelatedRecords requestAndRelatedRecords) {
    final Request request = requestAndRelatedRecords.getRequest();
    if (requestAndRelatedRecords.getSourceItemId().equals(request.getItemId())) {
      final RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();
      requestQueue.remove(request);
      return requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)
            .thenApply(r -> r.map(requestAndRelatedRecords::withRequestQueue));
    }
    else {
      return completedFuture(succeeded(requestAndRelatedRecords));
    }
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> onMovedTo(
    RequestAndRelatedRecords requestAndRelatedRecords) {
    final Request request = requestAndRelatedRecords.getRequest();
    if (requestAndRelatedRecords.getDestinationItemId().equals(request.getItemId())) {
      final RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();
      // NOTE: it is important to remove position when moving request from one queue to another
      request.removePosition();
      requestQueue.add(request);
      return requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)
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

  public CompletableFuture<Result<ReorderRequestContext>> onReorder(
    Result<ReorderRequestContext> result) {

    // 1st: set new positions for the requests in the queue
    return result.after(context -> {
      context.getReorderRequestToRequestMap().forEach(
        (reorderRequest, request) -> request.changePosition(reorderRequest.getNewPosition())
      );

      // 2nd: Call storage module to reorder requests.
      return completedFuture(succeeded(context))
        .thenApply(r -> r.map(ReorderRequestContext::getRequestQueue))
        .thenCompose(r -> r.after(requestQueueRepository::updateRequestsWithChangedPositions))
        .thenApply(r -> r.map(this::orderQueueByRequestPosition))
        .thenApply(r -> r.map(context::withRequestQueue));
    });
  }

  private RequestQueue orderQueueByRequestPosition(RequestQueue queue) {
    List<Request> requests = queue.getRequests()
      .stream()
      .sorted(comparingInt(Request::getPosition))
      .collect(Collectors.toList());

    return new RequestQueue(requests);
  }
}
