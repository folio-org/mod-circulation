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

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestCreated(
    RequestAndRelatedRecords records) {

    Request request = records.getRequest();

    if (request.hasItemId()) {
      sendConfirmationNoticeForRequestWithItemId(request);
    } else {
      sendConfirmationNoticeForRequestWithoutItemId(request);
    }

    return succeeded(records);
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestCancelled(
    RequestAndRelatedRecords records) {

    Request request = records.getRequest();

    if (request.hasItemId()) {
      sendCancellationNoticeForRequestWithItemId(request);
    } else {
      sendCancellationNoticeForRequestWithoutItemId(request);
    }

    return succeeded(records);
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestMoved(RequestAndRelatedRecords records) {
    sendNoticeOnRecall(records.getRequest());

    return succeeded(records);
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestUpdated(
    RequestAndRelatedRecords records) {

    if (records.getRequest().getStatus() == RequestStatus.CLOSED_CANCELLED) {
      requestRepository.loadCancellationReason(records.getRequest())
        .thenApply(r -> r.map(records::withRequest))
        .thenAccept(r -> r.next(this::sendNoticeOnRequestCancelled));
    }

    return succeeded(records);
  }

  public Result<CheckInContext> sendNoticeOnRequestAwaitingPickup(CheckInContext context) {
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
        .filter(Request::hasTopPriority)
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
      return ofAsync(request);
    }

    return ofAsync(item.getLocation())
      .thenCompose(r -> r.after(locationRepository::loadCampus))
      .thenCompose(r -> r.after(locationRepository::loadInstitution))
      .thenApply(r -> r.map(request.getItem()::withLocation))
      .thenApply(r -> r.map(request::withItem));
  }

  private CompletableFuture<Result<Void>> sendConfirmationNoticeForRequestWithItem(Request request) {
    PatronNoticeEvent event = createPatronNoticeEvent(request, getEventType(request));

    return patronNoticeService.acceptNoticeEvent(event)
      .whenComplete((r, t) -> sendNoticeOnRecall(request));
  }

  private CompletableFuture<Result<Void>> sendConfirmationNoticeForRequestWithoutItemId(
    Request request) {

    return sendNoticeForRequestWithoutItemId(request, getEventType(request),
      TlrSettingsConfiguration::getConfirmationPatronNoticeTemplateId);
  }

  private CompletableFuture<Result<Void>> sendCancellationNoticeForRequestWithItemId(
    Request request) {

    return patronNoticeService.acceptNoticeEvent(
      createPatronNoticeEvent(request, REQUEST_CANCELLATION));
  }

  private CompletableFuture<Result<Void>> sendCancellationNoticeForRequestWithoutItemId(
    Request request) {

    return sendNoticeForRequestWithoutItemId(request, REQUEST_CANCELLATION,
      TlrSettingsConfiguration::getCancellationPatronNoticeTemplateId);
  }

  private CompletableFuture<Result<Void>> sendNoticeForRequestWithoutItemId(Request request,
    NoticeEventType eventType, Function<TlrSettingsConfiguration, UUID> templateIdExtractor) {

    TlrSettingsConfiguration tlrSettings = request.getTlrSettingsConfiguration();
    UUID templateId = templateIdExtractor.apply(tlrSettings);

    if (request.isTitleLevel() && tlrSettings.isTitleLevelRequestsFeatureEnabled() && templateId != null) {
      return sendNotice(request, templateId, eventType);
    }

    return emptyAsync();
  }

  private CompletableFuture<Result<Void>> sendNotice(Request request, UUID templateId,
    NoticeEventType eventType) {

    JsonObject noticeContext = createRequestNoticeContext(request);
    NoticeLogContext noticeLogContext = NoticeLogContext.from(request)
      .withTriggeringEvent(eventType.getRepresentation())
      .withTemplateId(templateId.toString());
    PatronNotice notice = buildEmail(request.getUserId(), templateId, noticeContext);

    return patronNoticeService.sendNotice(notice, noticeLogContext);
  }

  private CompletableFuture<Result<Void>> fetchDataAndSendRequestAwaitingPickupNotice(
    Request request) {

    return ofAsync(() -> request)
      .thenCompose(r -> r.combineAfter(this::fetchServicePoint, Request::withPickupServicePoint))
      .thenCompose(r -> r.combineAfter(this::fetchRequester, Request::withRequester))
      .thenApply(r -> r.mapFailure(failure -> publishNoticeErrorEvent(failure, request)))
      .thenApply(r -> r.map(req -> createPatronNoticeEvent(req, AVAILABLE)))
      .thenCompose(r -> r.after(patronNoticeService::acceptNoticeEvent));
  }

  private CompletableFuture<Result<User>> fetchRequester(Request request) {
    String requesterId = request.getRequesterId();

    return userRepository.getUser(requesterId)
      .thenApply(r -> r.failWhen(this::isNull,
        user -> new RecordNotFoundFailure("user", requesterId)));
  }

  private CompletableFuture<Result<ServicePoint>> fetchServicePoint(Request request) {
    String pickupServicePointId = request.getPickupServicePointId();

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

    if (!request.isRecall() || loan == null || loan.getUser() == null || loan.getItem() == null) {
      return ofAsync(null);
    }

    PatronNoticeEvent itemRecalledEvent = new PatronNoticeEventBuilder()
      .withItem(loan.getItem())
      .withUser(loan.getUser())
      .withEventType(ITEM_RECALLED)
      .withNoticeContext(createLoanNoticeContext(loan))
      .withNoticeLogContext(NoticeLogContext.from(loan))
      .build();

    eventPublisher.publishRecallRequestedEvent(loan);

    return patronNoticeService.acceptNoticeEvent(itemRecalledEvent);
  }

  private static PatronNoticeEvent createPatronNoticeEvent(Request request,
    NoticeEventType noticeEventType) {

    return new PatronNoticeEventBuilder()
      .withUser(request.getRequester())
      .withEventType(noticeEventType)
      .withItem(request.hasItem() ? request.getItem() : null)
      .withNoticeContext(createRequestNoticeContext(request))
      .withNoticeLogContext(NoticeLogContext.from(request))
      .build();
  }

  private static NoticeEventType getEventType(Request request) {
    return requestTypeToEventMap.getOrDefault(request.getRequestType(), NoticeEventType.UNKNOWN);
  }

  private Result<Boolean> isNull(Object o) {
    return succeeded(o == null);
  }

}
