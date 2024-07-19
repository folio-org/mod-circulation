package org.folio.circulation.domain;

import static java.util.Comparator.comparingInt;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.determineClosedLibraryStrategyForHoldShelfExpirationDate;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;

import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.policy.ExpirationDateManagement;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategy;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.resources.context.ReorderRequestContext;
import org.folio.circulation.services.RequestQueueService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class UpdateRequestQueue {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final RequestQueueRepository requestQueueRepository;
  private final RequestRepository requestRepository;
  private final ServicePointRepository servicePointRepository;
  private final ConfigurationRepository configurationRepository;
  private final RequestQueueService requestQueueService;
  private final CalendarRepository calendarRepository;
  private static final String NOT_DEFINED_INTERVAL = "";

  public UpdateRequestQueue(
    RequestQueueRepository requestQueueRepository,
    RequestRepository requestRepository,
    ServicePointRepository servicePointRepository,
    ConfigurationRepository configurationRepository,
    RequestQueueService requestQueueService,
    CalendarRepository calendarRepository) {

    this.requestQueueRepository = requestQueueRepository;
    this.requestRepository = requestRepository;
    this.servicePointRepository = servicePointRepository;
    this.configurationRepository = configurationRepository;
    this.requestQueueService = requestQueueService;
    this.calendarRepository = calendarRepository;
  }

  public static UpdateRequestQueue using(Clients clients,
    RequestRepository requestRepository,
    RequestQueueRepository requestQueueRepository) {

    return new UpdateRequestQueue(requestQueueRepository,
      requestRepository, new ServicePointRepository(clients), new ConfigurationRepository(clients),
      RequestQueueService.using(clients), new CalendarRepository(clients));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> onCheckIn(
    LoanAndRelatedRecords relatedRecords) {

    log.debug("onCheckIn:: parameters relatedRecords: {}", () -> relatedRecords);

    //Do not attempt check in for open loan
    if(relatedRecords.getLoan().isOpen()) {
      return ofAsync(() -> relatedRecords);
    }

    final RequestQueue requestQueue = relatedRecords.getRequestQueue();
    final Item item = relatedRecords.getLoan().getItem();
    final String checkInServicePointId = relatedRecords.getLoan().getCheckInServicePointId();

    return onCheckIn(requestQueue, item, checkInServicePointId)
      .thenApply(result -> result.map(relatedRecords::withRequestQueue));
  }

  public CompletableFuture<Result<RequestQueue>> onCheckIn(
    RequestQueue requestQueue, Item item, String checkInServicePointId) {

    log.debug("onCheckIn:: parameters requestQueue: {}, item: {}, checkInServicePointId: {}",
      () -> requestQueue, () -> item, () -> checkInServicePointId);

    return requestQueueService.findRequestFulfillableByItem(item, requestQueue)
      .thenCompose(r -> r.after(request -> updateOutstandingRequestOnCheckIn(
        request, requestQueue, item, checkInServicePointId)));
  }

  private CompletableFuture<Result<RequestQueue>> updateOutstandingRequestOnCheckIn(
    Request requestBeingFulfilled, RequestQueue requestQueue, Item item, String checkInServicePointId) {

    log.info("updateOutstandingRequestOnCheckIn :: checkInServicePointId:{} ",checkInServicePointId);

    if (requestBeingFulfilled == null) {
      return ofAsync(requestQueue);
    }

    if (requestBeingFulfilled.getItemId() == null || !requestBeingFulfilled.isFor(item)) {
      requestBeingFulfilled = requestBeingFulfilled.withItem(item);
      log.info("updateOutstandingRequestOnCheckIn:: replacing request in the queue because " +
        "another instance of it has been created");
      // Replacing request in the queue because another instance of it has been created
      requestQueue.replaceRequest(requestBeingFulfilled);
    }

    requestQueue.updateRequestPositionOnCheckIn(requestBeingFulfilled.getId());

    Request originalRequest = Request.from(requestBeingFulfilled.asJson());

    CompletableFuture<Result<Request>> updatedReq;

    log.info("updateOutstandingRequestOnCheckIn:: preference:{} ",
      requestBeingFulfilled.getfulfillmentPreference());
    log.info("updateOutstandingRequestOnCheckIn:: requestBeingFulfilled.pickupServicePointId:{} ",
      requestBeingFulfilled.getPickupServicePointId());

    switch (requestBeingFulfilled.getfulfillmentPreference()) {
      case HOLD_SHELF:
        if (checkInServicePointId.equalsIgnoreCase(requestBeingFulfilled.getPickupServicePointId())) {
          log.info("updateOutstandingRequestOnCheckIn:: Updating to awaitingPickUp");
          return awaitPickup(requestBeingFulfilled,requestQueue);
        } else {
          log.info("updateOutstandingRequestOnCheckIn:: Updating to inTransit");
          updatedReq = putInTransit(requestBeingFulfilled);
        }

        break;
      case DELIVERY:
        updatedReq = awaitDelivery(requestBeingFulfilled);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " +
          requestBeingFulfilled.getfulfillmentPreference());
    }

    Request updatedRequest = Request.from(requestBeingFulfilled.asJson());
    requestQueue.update(originalRequest, updatedRequest);

    return updatedReq
      .thenComposeAsync(r -> r.after(requestRepository::update))
      .thenComposeAsync(result -> result.after(v -> requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)));
  }

  private CompletableFuture<Result<RequestQueue>> awaitPickup(Request request,
    RequestQueue requestQueue) {

    log.debug("awaitPickup:: parameters request: {}, requestQueue: {}",
      () -> request, () -> requestQueue);
    Request originalRequest = Request.from(request.asJson());
    request.changeStatus(RequestStatus.OPEN_AWAITING_PICKUP);

    if (request.getHoldShelfExpirationDate() == null) {
      log.info("awaitPickup:: holdShelfExpirationDate for request {} is null", request.getId());
      String pickupServicePointId = request.getPickupServicePointId();

      return servicePointRepository.getServicePointById(pickupServicePointId)
        .thenCombineAsync(configurationRepository.findTimeZoneConfiguration(),
          Result.combined((servicePoint, tenantTimeZone) ->
            populateHoldShelfExpirationDate(
              request.withPickupServicePoint(servicePoint),
              tenantTimeZone
            ).map(calculatedRequest-> setHoldShelfExpirationDateWithExpirationDateManagement(tenantTimeZone, calculatedRequest,
              requestQueue, originalRequest)))
        );
    } else {
      Request updatedRequest = Request.from(request.asJson());
      requestQueue.update(originalRequest, updatedRequest);

      return requestRepository.update(request)
        .thenComposeAsync(result -> result.after(v -> requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)));
    }
  }

  private RequestQueue setHoldShelfExpirationDateWithExpirationDateManagement(
    ZoneId tenantTimeZone, Request calculatedRequest, RequestQueue requestQueue,
    Request originalRequest) {

    ExpirationDateManagement expirationDateManagement = calculatedRequest.getPickupServicePoint()
      .getHoldShelfClosedLibraryDateManagement();

    String intervalId = Optional.of(calculatedRequest)
      .map(Request::getPickupServicePoint)
      .map(ServicePoint::getHoldShelfExpiryPeriod)
      .map(TimePeriod::getIntervalId)
      .map(String::toUpperCase)
      .orElse(NOT_DEFINED_INTERVAL);

    log.info("setHoldShelfExpirationDateWithExpirationDateManagement:: interval: {}", intervalId);

    log.info("setHoldShelfExpirationDateWithExpirationDateManagement expDate before:{}",
      calculatedRequest.getHoldShelfExpirationDate());
    // Old data where strategy is not set so default value but TimePeriod has MINUTES / HOURS
    if (ExpirationDateManagement.KEEP_THE_CURRENT_DUE_DATE == expirationDateManagement && isShortTerm(intervalId)) {
      expirationDateManagement = ExpirationDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME;
    }

    ExpirationDateManagement finalExpirationDateManagement = expirationDateManagement;

    ClosedLibraryStrategy closedLibraryStrategy = determineClosedLibraryStrategyForHoldShelfExpirationDate
      (finalExpirationDateManagement, calculatedRequest.getHoldShelfExpirationDate(), tenantTimeZone, calculatedRequest.getPickupServicePoint().getHoldShelfExpiryPeriod());
    calendarRepository.lookupOpeningDays(calculatedRequest.getHoldShelfExpirationDate().withZoneSameInstant(tenantTimeZone).toLocalDate(), calculatedRequest.getPickupServicePoint().getId())
      .thenApply(adjacentOpeningDaysResult -> closedLibraryStrategy.calculateDueDate(calculatedRequest.getHoldShelfExpirationDate(), adjacentOpeningDaysResult.value()))
      .thenApply(calculatedDate -> {
        log.info("calculatedDate after :{}",calculatedDate.value());
        calculatedRequest.changeHoldShelfExpirationDate(calculatedDate.value());
        requestQueue.update(originalRequest,calculatedRequest);

        return requestRepository.update(calculatedRequest)
          .thenComposeAsync(result -> result.after(v -> requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)));
      });
    return requestQueue;
  }

  private boolean isShortTerm(String intervalId) {

    return List.of("MINUTES", "HOURS").contains(intervalId);
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

  private Result<Request> populateHoldShelfExpirationDate(Request request, ZoneId tenantTimeZone) {
    log.debug("populateHoldShelfExpirationDate:: parameters request: {}, tenantTimeZone: {}",
      () -> request, () -> tenantTimeZone);
    ServicePoint pickupServicePoint = request.getPickupServicePoint();
    TimePeriod holdShelfExpiryPeriod = pickupServicePoint.getHoldShelfExpiryPeriod();

    log.debug("populateHoldShelfExpirationDate:: using time zone {} and period {}",
      () -> tenantTimeZone, holdShelfExpiryPeriod::getInterval);
    ZonedDateTime holdShelfExpirationDate = calculateHoldShelfExpirationDate(
      holdShelfExpiryPeriod, tenantTimeZone);
    request.changeHoldShelfExpirationDate(holdShelfExpirationDate);

    return succeeded(request);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> onCheckOut(LoanAndRelatedRecords records) {
    return requestQueueService.findRequestFulfillableByItem(records.getItem(), records.getRequestQueue())
      .thenCompose(r -> r.after(request -> onCheckOut(records, request)));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> onCheckOut(
    LoanAndRelatedRecords relatedRecords, Request firstRequest) {

    log.debug("onCheckOut:: parameters relatedRecords: {}, firstRequest: {}",
      () -> relatedRecords, () -> firstRequest);
    if (firstRequest == null) {
      log.info("onCheckOut:: first request is null");
      return completedFuture(succeeded(relatedRecords));
    }

    RequestQueue requestQueue = relatedRecords.getRequestQueue();
    Request originalRequest = Request.from(firstRequest.asJson());

    log.info("onCheckOut:: Closing request '{}'", firstRequest.getId());
    firstRequest.changeStatus(RequestStatus.CLOSED_FILLED);

    log.info("onCheckOut:: Removing request '{}' from queue", firstRequest.getId());
    requestQueue.remove(firstRequest);

    Request updatedRequest = Request.from(firstRequest.asJson());

    requestQueue.update(originalRequest, updatedRequest);

    return requestRepository.update(firstRequest)
      .thenComposeAsync(r -> r.after(v ->
        requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)))
      .thenApply(r -> r.map(relatedRecords::withRequestQueue))
      .thenApply(r -> r.map(v -> v.withClosedFilledRequest(firstRequest)));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> onCreate(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("onCreate:: parameters requestAndRelatedRecords: {}", () -> requestAndRelatedRecords);
    final Request request = requestAndRelatedRecords.getRequest();
    final RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();
    requestQueue.add(request);

    return requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)
        .thenApply(r -> r.map(requestAndRelatedRecords::withRequestQueue));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> onCancellation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("onCancellation:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    if(requestAndRelatedRecords.getRequest().isCancelled()) {
      log.info("onCancellation:: request is cancelled");
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

    log.debug("onMovedFrom:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    final Request request = requestAndRelatedRecords.getRequest();
    if (requestAndRelatedRecords.getSourceItemId().equals(request.getItemId()) &&
      !requestAndRelatedRecords.isTlrFeatureEnabled()) {

      log.info("onMovedFrom:: removing request from the requestQueue");
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

    log.debug("onMovedTo:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    final Request request = requestAndRelatedRecords.getRequest();
    if (requestAndRelatedRecords.getDestinationItemId().equals(request.getItemId())) {
      final RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();
      // NOTE: it is important to remove position when moving request from one queue to another
      if (requestAndRelatedRecords.isTlrFeatureEnabled()) {
        log.info("onMovedTo:: removing request from the requestQueue");
        requestQueue.remove(request);
      }
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
    log.debug("onDeletion:: parameters request: {}", () -> request);

    return requestQueueRepository.getByItemId(request.getItemId())
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

  private ZonedDateTime calculateHoldShelfExpirationDate(
    TimePeriod holdShelfExpiryPeriod, ZoneId tenantTimeZone) {

    ZonedDateTime now = getZonedDateTime().withZoneSameInstant(tenantTimeZone);

    ZonedDateTime holdShelfExpirationDate = holdShelfExpiryPeriod.getInterval()
      .addTo(now, holdShelfExpiryPeriod.getDuration());
    if (holdShelfExpiryPeriod.isLongTermPeriod()) {
      log.info("calculateHoldShelfExpirationDate:: holdShelfExpiryPeriod is long term");
      holdShelfExpirationDate = atEndOfDay(holdShelfExpirationDate);
    }

    return holdShelfExpirationDate;
  }
}
