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
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.policy.ExpirationDateManagement;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategy;
import org.folio.circulation.domain.validation.RequestQueueValidation;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.RequestQueueLockRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.resources.context.ReorderRequestContext;
import org.folio.circulation.services.RequestQueueLockService;
import org.folio.circulation.services.RequestQueueService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.Vertx;

public class UpdateRequestQueue {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final RequestQueueRepository requestQueueRepository;
  private final RequestRepository requestRepository;
  private final ServicePointRepository servicePointRepository;
  private final SettingsRepository settingsRepository;
  private final RequestQueueService requestQueueService;
  private final CalendarRepository calendarRepository;
  private final RequestQueueLockService requestQueueLockService;
  private static final String NOT_DEFINED_INTERVAL = "";

  public UpdateRequestQueue(
    RequestQueueRepository requestQueueRepository,
    RequestRepository requestRepository,
    ServicePointRepository servicePointRepository,
    SettingsRepository settingsRepository,
    RequestQueueService requestQueueService,
    CalendarRepository calendarRepository,
    RequestQueueLockService requestQueueLockService) {

    this.requestQueueRepository = requestQueueRepository;
    this.requestRepository = requestRepository;
    this.servicePointRepository = servicePointRepository;
    this.settingsRepository = settingsRepository;
    this.requestQueueService = requestQueueService;
    this.calendarRepository = calendarRepository;
    this.requestQueueLockService = requestQueueLockService;
  }

  public static UpdateRequestQueue using(Clients clients,
    RequestRepository requestRepository,
    RequestQueueRepository requestQueueRepository,
    Vertx vertx) {

    return new UpdateRequestQueue(requestQueueRepository,
      requestRepository, new ServicePointRepository(clients), new SettingsRepository(clients),
      RequestQueueService.using(clients), new CalendarRepository(clients),
      new RequestQueueLockService(new RequestQueueLockRepository(clients), vertx));
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

    return onCheckIn(requestQueue, item, checkInServicePointId, relatedRecords.getTlrSettings())
      .thenApply(result -> result.map(relatedRecords::withRequestQueue));
  }

  public CompletableFuture<Result<RequestQueue>> onCheckIn(
    RequestQueue requestQueue, Item item, String checkInServicePointId,
    org.folio.circulation.domain.configuration.TlrSettingsConfiguration tlrSettings) {

    log.debug("onCheckIn:: parameters requestQueue: {}, item: {}, checkInServicePointId: {}",
      () -> requestQueue, () -> item, () -> checkInServicePointId);

    return requestQueueService.findRequestFulfillableByItem(item, requestQueue)
      .thenCompose(r -> r.after(request -> prepareOutstandingRequestOnCheckIn(
        request, item, checkInServicePointId)))
      .thenCompose(r -> r.after(request -> persistCheckInQueueUpdate(
        request, requestQueue, item, tlrSettings)));
  }

  public Result<RequestAndRelatedRecords> prepareForCreate(
    RequestAndRelatedRecords relatedRecords) {

    RequestQueue requestQueue = relatedRecords.getRequestQueue();
    requestQueue.add(relatedRecords.getRequest());
    return succeeded(relatedRecords);
  }

  private CompletableFuture<Result<Request>> prepareOutstandingRequestOnCheckIn(
    Request requestBeingFulfilled, Item item, String checkInServicePointId) {

    log.info("updateOutstandingRequestOnCheckIn :: checkInServicePointId:{} ",checkInServicePointId);

    if (requestBeingFulfilled == null) {
      return ofAsync(() -> null);
    }

    if (requestBeingFulfilled.getItemId() == null || !requestBeingFulfilled.isFor(item)) {
      requestBeingFulfilled = requestBeingFulfilled.withItem(item);
      log.info("prepareOutstandingRequestOnCheckIn:: assigning the checked-in item to request");
    }

    CompletableFuture<Result<Request>> updatedReq;

    log.info("updateOutstandingRequestOnCheckIn:: preference:{} ",
      requestBeingFulfilled.getfulfillmentPreference());
    log.info("updateOutstandingRequestOnCheckIn:: requestBeingFulfilled.pickupServicePointId:{} ",
      requestBeingFulfilled.getPickupServicePointId());

    switch (requestBeingFulfilled.getfulfillmentPreference()) {
      case HOLD_SHELF:
        if (checkInServicePointId.equalsIgnoreCase(requestBeingFulfilled.getPickupServicePointId())) {
          log.info("updateOutstandingRequestOnCheckIn:: Updating to awaitingPickUp");
          return awaitPickup(requestBeingFulfilled);
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

    return updatedReq;
  }

  private CompletableFuture<Result<RequestQueue>> persistCheckInQueueUpdate(
    Request preparedRequest, RequestQueue initialQueue, Item item,
    org.folio.circulation.domain.configuration.TlrSettingsConfiguration tlrSettings) {

    if (preparedRequest == null) {
      return completedFuture(succeeded(initialQueue));
    }

    // The request instance is authoritative for TLR queues. This matters for DCB
    // requests, where the temporary circulation item's instance can differ from
    // the instance retained on the request.
    RequestQueueKey key = RequestQueueKey.from(preparedRequest, tlrSettings);
    return executeWithLock(key, () -> requestQueueRepository
      .getLightweightForPositioning(key)
      .thenApply(r -> r.map(queue -> queue.withRelatedRecordsFrom(initialQueue)))
      .thenCompose(r -> r.after(queue -> persistPreparedCheckInRequest(
        preparedRequest, queue))));
  }

  private CompletableFuture<Result<RequestQueue>> persistPreparedCheckInRequest(
    Request preparedRequest, RequestQueue requestQueue) {

    Request currentRequest = requestQueue.findById(preparedRequest.getId());
    if (currentRequest == null) {
      log.info("Request {} left the queue before check-in positioning", preparedRequest.getId());
      return completedFuture(succeeded(requestQueue));
    }

    Request originalRequest = Request.from(currentRequest.asJson());
    var updatedRepresentation = preparedRequest.asJson();
    if (currentRequest.getPosition() == null) {
      updatedRepresentation.remove("position");
    } else {
      updatedRepresentation.put("position", currentRequest.getPosition());
    }

    Request updatedRequest = currentRequest.withRequestRepresentation(updatedRepresentation);
    requestQueue.replaceRequest(updatedRequest);
    requestQueue.updateRequestPositionOnCheckIn(updatedRequest.getId());
    updatedRequest = requestQueue.findById(updatedRequest.getId());
    requestQueue.update(originalRequest, Request.from(updatedRequest.asJson()));

    Request requestToUpdate = updatedRequest;
    if (requestToUpdate.hasChangedPosition()) {
      // The batch stores the status and all position changes in one storage transaction.
      return requestQueueRepository.updateRequestsWithChangedPositions(requestQueue);
    }

    return requestRepository.update(requestToUpdate)
      .thenApply(r -> r.map(v -> requestQueue));
  }

  private CompletableFuture<Result<Request>> awaitPickup(Request request) {

    log.debug("awaitPickup:: parameters request: {}", () -> request);
    request.changeStatus(RequestStatus.OPEN_AWAITING_PICKUP);

    if (request.getHoldShelfExpirationDate() == null) {
      log.info("awaitPickup:: holdShelfExpirationDate for request {} is null", request.getId());
      String pickupServicePointId = request.getPickupServicePointId();

      return servicePointRepository.getServicePointById(pickupServicePointId)
        .thenCombineAsync(settingsRepository.lookupTimeZoneSettings(),
          Result.combined((servicePoint, tenantTimeZone) ->
            populateHoldShelfExpirationDate(
              request.withPickupServicePoint(servicePoint),
              tenantTimeZone
            ).map(calculatedRequest -> new RequestWithTimeZone(calculatedRequest, tenantTimeZone))))
        .thenCompose(r -> r.after(requestWithTimeZone ->
          setHoldShelfExpirationDateWithExpirationDateManagement(
            requestWithTimeZone.tenantTimeZone, requestWithTimeZone.request)));
    } else {
      return completedFuture(succeeded(request));
    }
  }

  private CompletableFuture<Result<Request>> setHoldShelfExpirationDateWithExpirationDateManagement(
    ZoneId tenantTimeZone, Request calculatedRequest) {

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
    return calendarRepository.lookupOpeningDays(calculatedRequest.getHoldShelfExpirationDate().withZoneSameInstant(tenantTimeZone).toLocalDate(), calculatedRequest.getPickupServicePoint().getId())
      .thenApply(adjacentOpeningDaysResult -> closedLibraryStrategy.calculateDueDate(calculatedRequest.getHoldShelfExpirationDate(), adjacentOpeningDaysResult.value()))
      .thenCompose(calculatedDateResult -> calculatedDateResult.after(calculatedDate -> {
        log.info("setHoldShelfExpirationDateWithExpirationDateManagement:: calculatedDate after: {}", calculatedDate);
        calculatedRequest.changeHoldShelfExpirationDate(calculatedDate);
        return completedFuture(succeeded(calculatedRequest));
      }));
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

    // Use the selected request to identify a TLR queue. A DCB circulation item can
    // belong to a different instance from the request it fulfils.
    RequestQueueKey key = RequestQueueKey.from(firstRequest,
      relatedRecords.getTlrSettings());
    return executeWithLock(key, () -> requestQueueRepository
      .getLightweightForPositioning(key)
      .thenApply(r -> r.map(queue -> queue.withRelatedRecordsFrom(
        relatedRecords.getRequestQueue())))
      .thenCompose(r -> r.after(queue -> closeRequestOnCheckOut(
        relatedRecords.withRequestQueue(queue), firstRequest.getId()))));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> closeRequestOnCheckOut(
    LoanAndRelatedRecords relatedRecords, String requestId) {

    RequestQueue requestQueue = relatedRecords.getRequestQueue();
    Request firstRequest = requestQueue.findById(requestId);
    if (firstRequest == null) {
      log.info("Request {} left the queue before check-out positioning", requestId);
      return completedFuture(succeeded(relatedRecords));
    }

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

  CompletableFuture<Result<RequestAndRelatedRecords>> onCancellation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("onCancellation:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    if(requestAndRelatedRecords.getRequest().isCancelled()) {
      log.info("onCancellation:: request is cancelled");
      RequestQueueKey key = RequestQueueKey.from(requestAndRelatedRecords);
      return executeWithLock(key, () -> requestQueueRepository
        .getLightweightForPositioning(key)
        .thenApply(r -> r.map(queue -> queue.withRelatedRecordsFrom(
          requestAndRelatedRecords.getRequestQueue())))
        .thenCompose(r -> r.after(queue -> {
          Request request = requestAndRelatedRecords.getRequest();
          queue.remove(request);
          return requestRepository.update(request)
            .thenCompose(updateResult -> updateResult.after(v ->
              requestQueueRepository.updateRequestsWithChangedPositions(queue)))
            .thenApply(updateResult -> updateResult.map(
              requestAndRelatedRecords::withRequestQueue));
        })));
    }
    else {
      return requestRepository.update(requestAndRelatedRecords);
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
      RequestQueueKey key = RequestQueueKey.forItem(
        requestAndRelatedRecords.getSourceItemId());
      return executeWithLock(key, () -> requestQueueRepository
        .getLightweightForPositioning(key)
        .thenApply(r -> r.map(queue -> queue.withRelatedRecordsFrom(
          requestAndRelatedRecords.getRequestQueue())))
        .thenCompose(r -> r.after(requestQueue -> {
          requestQueue.remove(request);
          return requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)
            .thenApply(updateResult -> updateResult.map(
              requestAndRelatedRecords::withRequestQueue));
        })));
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
      RequestQueueKey key = requestAndRelatedRecords.isTlrFeatureEnabled()
        ? RequestQueueKey.forInstance(request.getInstanceId())
        : RequestQueueKey.forItem(requestAndRelatedRecords.getDestinationItemId());
      return executeWithLock(key, () -> requestQueueRepository
        .getLightweightForPositioning(key)
        .thenApply(r -> r.map(queue -> queue.withRelatedRecordsFrom(
          requestAndRelatedRecords.getRequestQueue())))
        .thenCompose(r -> r.after(requestQueue -> {
          // NOTE: it is important to remove position when moving request from one queue to another
          if (requestAndRelatedRecords.isTlrFeatureEnabled()) {
            log.info("onMovedTo:: removing request from the requestQueue");
            requestQueue.remove(request);
          }
          request.removePosition();
          requestQueue.add(request);
          return requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)
            .thenApply(updateResult -> updateResult.map(
              requestAndRelatedRecords::withRequestQueue));
        })));
    }
    else {
      return completedFuture(succeeded(requestAndRelatedRecords));
    }
  }

  public CompletableFuture<Result<Request>> onDeletion(Request request,
    org.folio.circulation.domain.configuration.TlrSettingsConfiguration tlrSettings) {

    log.debug("onDeletion:: parameters request: {}", () -> request);

    if (request.getPosition() == null) {
      return requestRepository.delete(request);
    }

    RequestQueueKey key = RequestQueueKey.from(request, tlrSettings);
    return executeWithLock(key, () -> requestQueueRepository
      .getLightweightForPositioning(key)
      .thenCompose(r -> r.after(requestQueue -> requestRepository.delete(request)
        .thenCompose(deleteResult -> deleteResult.after(deletedRequest -> {
          requestQueue.remove(deletedRequest);
          return requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)
            .thenApply(updateResult -> updateResult.map(queue -> deletedRequest));
        })))));
  }

  public CompletableFuture<Result<ReorderRequestContext>> onReorder(
    Result<ReorderRequestContext> result) {

    return result.after(context -> {
      RequestQueueKey key = RequestQueueKey.from(context);
      RequestQueue initialQueue = context.getRequestQueue();
      return executeWithLock(key, () -> requestQueueRepository
        .getLightweightForPositioning(key)
        .thenApply(r -> r.map(queue -> context.withRequestQueue(
          queue.withRelatedRecordsFrom(initialQueue))))
        .thenApply(RequestQueueValidation::queueIsFound)
        .thenApply(RequestQueueValidation::positionsAreSequential)
        .thenApply(RequestQueueValidation::queueIsConsistent)
        .thenApply(RequestQueueValidation::pageRequestsPositioning)
        .thenApply(RequestQueueValidation::fulfillingRequestsPositioning)
        .thenCompose(r -> r.after(freshContext -> {
          freshContext.getReorderRequestToRequestMap().forEach(
            (reorderRequest, request) -> request.changePosition(
              reorderRequest.getNewPosition()));

          return requestQueueRepository.updateRequestsWithChangedPositions(
              freshContext.getRequestQueue())
            .thenApply(updateResult -> updateResult.map(this::orderQueueByRequestPosition))
            .thenApply(updateResult -> updateResult.map(freshContext::withRequestQueue));
        })));
    });
  }

  private RequestQueue orderQueueByRequestPosition(RequestQueue queue) {
    List<Request> requests = queue.getRequests()
      .stream()
      .sorted(comparingInt(Request::getPosition))
      .toList();

    return new RequestQueue(requests);
  }

  private <T> CompletableFuture<Result<T>> executeWithLock(RequestQueueKey key,
    Supplier<CompletableFuture<Result<T>>> criticalSection) {

    return settingsRepository.lookupRequestQueueLockSettings()
      .thenCompose(configurationResult -> configurationResult.after(configuration ->
        requestQueueLockService.execute(key, configuration, criticalSection)));
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

  /**
   * Simple record to hold a {@link Request} and its associated tenant {@link ZoneId}.
   */
  private record RequestWithTimeZone(Request request, ZoneId tenantTimeZone) {}
}
