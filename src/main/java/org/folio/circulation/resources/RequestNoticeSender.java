package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.NoticeEventType.AVAILABLE;
import static org.folio.circulation.domain.notice.NoticeEventType.ITEM_RECALLED;
import static org.folio.circulation.domain.notice.NoticeEventType.REQUEST_CANCELLATION;
import static org.folio.circulation.domain.notice.PatronNotice.buildEmail;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContext;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createRequestNoticeContext;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.notice.ImmediatePatronNoticeService;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNotice;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.SingleImmediatePatronNoticeService;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RequestNoticeSender {
  public RequestNoticeSender(Clients clients) {
    final var itemRepository = new ItemRepository(clients);

    userRepository = new UserRepository(clients);
    patronNoticeService = new SingleImmediatePatronNoticeService(clients);
    loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    requestRepository = RequestRepository.using(clients, itemRepository, userRepository, loanRepository);
    servicePointRepository = new ServicePointRepository(clients);
    eventPublisher = new EventPublisher(clients.pubSubPublishingService());
    locationRepository = LocationRepository.using(clients, servicePointRepository);
  }

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  protected static final Map<RequestType, NoticeEventType> requestTypeToEventMap;

  static {
    Map<RequestType, NoticeEventType> map = new EnumMap<>(RequestType.class);
    map.put(RequestType.PAGE, NoticeEventType.PAGING_REQUEST);
    map.put(RequestType.HOLD, NoticeEventType.HOLD_REQUEST);
    map.put(RequestType.RECALL, NoticeEventType.RECALL_REQUEST);
    requestTypeToEventMap = Collections.unmodifiableMap(map);
  }

  public static RequestNoticeSender using(Clients clients) {
    return new RequestNoticeSender(clients);
  }

  protected final ImmediatePatronNoticeService patronNoticeService;
  private final RequestRepository requestRepository;
  private final LoanRepository loanRepository;
  private final UserRepository userRepository;
  private final ServicePointRepository servicePointRepository;
  private final EventPublisher eventPublisher;
  protected final LocationRepository locationRepository;

  private long recallRequestCount = 0l;

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestCreated(
    RequestAndRelatedRecords records) {

    log.debug("sendNoticeOnRequestCreated:: parameters records: {}", () -> records);
    Request request = records.getRequest();
    recallRequestCount = records.getRequestQueue().getRequests()
      .stream()
      .filter(r -> r.getRequestType() == RequestType.RECALL && r.isNotYetFilled()
        && r.getItemId().equals(request.getItemId()))
      .count();

    if (request.hasItemId()) {
      sendConfirmationNoticeForRequestWithItemId(request);
    } else {
      sendConfirmationNoticeForRequestWithoutItemId(request);
    }

    return succeeded(records);
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestCancelled(
    RequestAndRelatedRecords records) {

    log.debug("sendNoticeOnRequestCancelled:: parameters records: {}", () -> records);
    Request request = records.getRequest();

    if (!request.getDcbReRequestCancellationValue()) {
      if (request.hasItemId()) {
        sendCancellationNoticeForRequestWithItemId(request);
      } else {
        sendCancellationNoticeForRequestWithoutItemId(request);
      }
    }

    return succeeded(records);
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestMoved(RequestAndRelatedRecords records) {
    sendNoticeOnRecall(records.getRequest());
    return succeeded(records);
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestUpdated(
    RequestAndRelatedRecords records) {

    log.debug("sendNoticeOnRequestUpdated:: parameters records: {}", () -> records);
    if (records.getRequest().getStatus() == RequestStatus.CLOSED_CANCELLED) {
      requestRepository.loadCancellationReason(records.getRequest())
        .thenCompose(r -> r.after(this::fetchAdditionalInfo))
        .thenApply(r -> r.map(records::withRequest))
        .thenAccept(r -> r.next(this::sendNoticeOnRequestCancelled));
    }

    return succeeded(records);
  }

  private CompletableFuture<Result<Request>> fetchAdditionalInfo(Request request) {
    if (request.hasLoan()) {
      log.debug("fetchAdditionalInfo:: request has loan, fetch patron info");
      return loanRepository.fetchLatestPatronInfoAddedComment(request.getLoan())
        .thenApply(r -> r.map(request::withLoan));
    } else {
      return CompletableFuture.completedFuture(succeeded(request));
    }
  }

  public Result<CheckInContext> sendNoticeOnRequestAwaitingPickup(CheckInContext context) {
    log.debug("sendNoticeOnRequestAwaitingPickup:: parameters context: {}", () -> context);
    final Item item = context.getItem();
    final RequestQueue requestQueue = context.getRequestQueue();

    if (item == null || item.isNotFound()) {
      log.warn("Request Awaiting Pickup notice processing is aborted: item is missing");
    }
    else if (requestQueue == null) {
      log.warn("Request Awaiting Pickup notice processing is aborted: request queue is null");
    }
    else if (item.isAwaitingPickup()) {
      requestQueue.getRequests().stream()
        .filter(Request::isAwaitingPickup)
        .filter(Request::hasChangedStatus)
        .findFirst()
        .map(request -> request.withItem(item))
        .ifPresent(this::fetchDataAndSendRequestAwaitingPickupNotice);
    }

    return succeeded(context);
  }

  private CompletableFuture<Result<Void>> sendConfirmationNoticeForRequestWithItemId(
    Request request) {

    return fetchMissingLocationDetails(request)
      .thenCompose(r -> r.after(this::sendConfirmationNoticeForRequestWithItem));
  }

  private CompletableFuture<Result<Request>> fetchMissingLocationDetails(Request request) {
    Item item = request.getItem();

    if (item == null || item.isNotFound() || item.getLocation() == null) {
      log.info("fetchMissingLocationDetails:: location cannot be fetched, item: {}",
        request.getItemId());

      return ofAsync(request);
    }

    return ofAsync(item.getLocation())
      .thenCompose(r -> r.after(locationRepository::loadCampus))
      .thenCompose(r -> r.after(locationRepository::loadInstitution))
      .thenApply(r -> r.map(request.getItem()::withLocation))
      .thenApply(r -> r.map(request::withItem));
  }

  private CompletableFuture<Result<Void>> sendConfirmationNoticeForRequestWithItem(Request request) {
    log.debug("sendConfirmationNoticeForRequestWithItem:: parameters request: {}", () -> request);
    return createPatronNoticeEvent(request, getEventType(request))
      .thenCompose(r -> r.after(patronNoticeService::acceptNoticeEvent))
      .whenComplete((r, t) -> sendNoticeOnRecall(request));
  }

  private CompletableFuture<Result<Void>> sendConfirmationNoticeForRequestWithoutItemId(
    Request request) {

    log.debug("sendConfirmationNoticeForRequestWithoutItemId:: parameters request: {}", () -> request);

    return sendNoticeForRequestWithoutItemId(request, getEventType(request),
      TlrSettingsConfiguration::getConfirmationPatronNoticeTemplateId);
  }

  private CompletableFuture<Result<Void>> sendCancellationNoticeForRequestWithItemId(
    Request request) {

    log.debug("sendCancellationNoticeForRequestWithItemId:: parameters request: {}", () -> request);

    return createPatronNoticeEvent(request, REQUEST_CANCELLATION)
      .thenCompose(r -> r.after(patronNoticeService::acceptNoticeEvent));
  }

  private CompletableFuture<Result<Void>> sendCancellationNoticeForRequestWithoutItemId(
    Request request) {

    log.debug("sendCancellationNoticeForRequestWithoutItemId:: parameters request: {}",
      () -> request);

    return sendNoticeForRequestWithoutItemId(request, REQUEST_CANCELLATION,
      TlrSettingsConfiguration::getCancellationPatronNoticeTemplateId);
  }

  private CompletableFuture<Result<Void>> sendNoticeForRequestWithoutItemId(Request request,
    NoticeEventType eventType, Function<TlrSettingsConfiguration, UUID> templateIdExtractor) {

    TlrSettingsConfiguration tlrSettings = request.getTlrSettingsConfiguration();
    UUID templateId = templateIdExtractor.apply(tlrSettings);

    if (request.isTitleLevel() && tlrSettings.isTitleLevelRequestsFeatureEnabled() && templateId != null) {
      return fetchDataAndSendNotice(request, templateId, eventType);
    }

    return emptyAsync();
  }

  private CompletableFuture<Result<Void>> fetchDataAndSendNotice(Request request, UUID templateId,
    NoticeEventType eventType) {

    log.debug("sendNotice:: parameters request: {}, templateId: {}, eventType: {}",
      () -> request, () -> templateId, () -> eventType);

    return fetchAdditionalInfo(request)
      .thenCompose(r -> r.after(req -> sendNotice(req, templateId, eventType)));
  }

  private CompletableFuture<Result<Void>> sendNotice(Request request, UUID templateId,
    NoticeEventType eventType){

    JsonObject noticeContext = createRequestNoticeContext(request);
    NoticeLogContext noticeLogContext = NoticeLogContext.from(request)
      .withTriggeringEvent(eventType.getRepresentation())
      .withTemplateId(templateId.toString());
    PatronNotice notice = buildEmail(request.getUserId(), templateId, noticeContext);
    return patronNoticeService.sendNotice(notice, noticeLogContext);
  }

  private CompletableFuture<Result<Void>> fetchDataAndSendRequestAwaitingPickupNotice(
    Request request) {

    log.debug("fetchDataAndSendRequestAwaitingPickupNotice:: parameters request: {}",
      () -> request);
    return ofAsync(() -> request)
      .thenCompose(r -> r.combineAfter(this::fetchServicePoint, Request::withPickupServicePoint))
      .thenCompose(r -> r.combineAfter(this::fetchRequester, Request::withRequester))
      .thenApply(r -> r.mapFailure(failure -> publishNoticeErrorEvent(failure, request)))
      .thenCompose(r -> r.after(req -> createPatronNoticeEvent(req, AVAILABLE)))
      .thenCompose(r -> r.after(patronNoticeService::acceptNoticeEvent));
  }

  private CompletableFuture<Result<User>> fetchRequester(Request request) {
    String requesterId = request.getRequesterId();
    log.info("fetchRequester:: requesterId: {}", requesterId);

    return userRepository.getUserWithPatronGroup(requesterId)
      .thenApply(r -> r.failWhen(this::isNull,
        user -> new RecordNotFoundFailure("user", requesterId)));
  }

  private CompletableFuture<Result<ServicePoint>> fetchServicePoint(Request request) {
    String pickupServicePointId = request.getPickupServicePointId();
    log.info("fetchServicePoint:: pickupServicePointId: {}", pickupServicePointId);

    return servicePointRepository.getServicePointById(pickupServicePointId)
      .thenApply(r -> r.failWhen(this::isNull,
        sp -> new RecordNotFoundFailure("servicePoint", pickupServicePointId)));
  }

  private Result<Request> publishNoticeErrorEvent(HttpFailure failure, Request request) {
    log.error("Failed to send Request Awaiting Pickup notice for request {} to user {}. Cause: {}",
      request.getId(), request.getRequesterId(), failure);

    eventPublisher.publishNoticeErrorLogEvent(NoticeLogContext.from(request), failure);

    return failed(failure);
  }

  private CompletableFuture<Result<Void>> sendNoticeOnRecall(Request request) {
    Loan loan = request.getLoan();

    if (!request.isRecall() || loan == null || loan.getUser() == null
      || loan.getItem() == null || recallRequestCount > 1) {
      return ofAsync(null);
    }

    return fetchAdditionalInfo(request)
      .thenCompose(r -> r.after(req -> sendLoanNotice(req.getLoan())));
  }

  private CompletableFuture<Result<Void>> sendLoanNotice(Loan updatedLoan){
    PatronNoticeEvent itemRecalledEvent = new PatronNoticeEventBuilder()
      .withItem(updatedLoan.getItem())
      .withUser(updatedLoan.getUser())
      .withEventType(ITEM_RECALLED)
      .withNoticeContext(createLoanNoticeContext(updatedLoan))
      .withNoticeLogContext(NoticeLogContext.from(updatedLoan))
      .build();

    eventPublisher.publishRecallRequestedEvent(updatedLoan);
    return patronNoticeService.acceptNoticeEvent(itemRecalledEvent);
  }

  private CompletableFuture<Result<PatronNoticeEvent>> createPatronNoticeEvent(Request request,
    NoticeEventType noticeEventType) {

    return fetchAdditionalInfo(request)
      .thenApply(result -> result.map(updatedRequest -> new PatronNoticeEventBuilder()
        .withUser(updatedRequest.getRequester())
        .withEventType(noticeEventType)
        .withItem(updatedRequest.hasItem() ? updatedRequest.getItem() : null)
        .withNoticeContext(createRequestNoticeContext(updatedRequest))
        .withNoticeLogContext(NoticeLogContext.from(updatedRequest))
        .build()));
  }

  private static NoticeEventType getEventType(Request request) {
    return requestTypeToEventMap.getOrDefault(request.getRequestType(), NoticeEventType.UNKNOWN);
  }

  private Result<Boolean> isNull(Object o) {
    return succeeded(o == null);
  }

}
