package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.utils.DateTimeUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateRequestQueue {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final RequestQueueRepository requestQueueRepository;
  private final RequestRepository requestRepository;
  private final ServicePointRepository servicePointRepository;
  private final ConfigurationRepository configurationRepository;

  public UpdateRequestQueue(
    RequestQueueRepository requestQueueRepository,
    RequestRepository requestRepository,
    ServicePointRepository servicePointRepository,
    ConfigurationRepository configurationRepository) {

    this.requestQueueRepository = requestQueueRepository;
    this.requestRepository = requestRepository;
    this.servicePointRepository = servicePointRepository;
    this.configurationRepository = configurationRepository;
  }

  public static UpdateRequestQueue using(Clients clients) {
    return new UpdateRequestQueue(
      RequestQueueRepository.using(clients),
      RequestRepository.using(clients),
      new ServicePointRepository(clients),
      new ConfigurationRepository(clients));
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
            .thenCombineAsync(configurationRepository.findTimeZoneConfiguration(),
              Result.combined((servicePoint, tenantTimeZone) -> {

                TimePeriod holdShelfExpiryPeriod = servicePoint.getHoldShelfExpiryPeriod();
                ZonedDateTime holdShelfExpirationDate =
                  calculateHoldShelfExpirationDate(holdShelfExpiryPeriod, tenantTimeZone);

                log.debug("Using time zone {} and period {}",
                  tenantTimeZone,
                  holdShelfExpiryPeriod.getInterval()
                );

                // Need to use Joda time here since formatting/parsing using
                // java.time has issues with the ISO-8601 format FOLIO uses,
                // specifically: 2019-02-18T00:00:00.000+0000 cannot be parsed
                // due to a missing ':' in the offset. Parsing is possible if
                // the format is: 2019-02-18T00:00:00.000+00:00
                firstRequest.changeHoldShelfExpirationDate(new DateTime(
                  holdShelfExpirationDate.toInstant().toEpochMilli(),
                  DateTimeZone.UTC
                ));

                return succeeded(firstRequest);
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

  private ZonedDateTime calculateHoldShelfExpirationDate(
    TimePeriod holdShelfExpiryPeriod, DateTimeZone tenantTimeZone) {

    ZonedDateTime now = Instant.now(ClockManager.getClockManager().getClock())
      .atZone(tenantTimeZone.toTimeZone().toZoneId());

    ZonedDateTime holdShelfExpirationDate = holdShelfExpiryPeriod.getInterval()
      .addTo(now, holdShelfExpiryPeriod.getDuration());

    if (holdShelfExpiryPeriod.isLongTermPeriod()) {
      holdShelfExpirationDate = DateTimeUtil.atEndOfTheDay(holdShelfExpirationDate);
    }

    return holdShelfExpirationDate;
  }
}
