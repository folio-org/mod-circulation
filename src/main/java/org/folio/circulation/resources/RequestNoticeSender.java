package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.NoticeEventType.REQUEST_CANCELLATION;
import static org.folio.circulation.domain.notice.NoticeFormat.EMAIL;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createRequestNoticeContext;
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
import org.folio.circulation.domain.notice.NoticeConfiguration;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNotice;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.SingleImmediatePatronNoticeService;
import org.folio.circulation.domain.notice.TemplateContextUtil;
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
    RequestAndRelatedRecords relatedRecords) {

    return relatedRecords.getRequest().hasItemId()
      ? sendConfirmationNoticeForRequestWithItemId(relatedRecords)
      : sendConfirmationNoticeForRequestWithoutItemId(relatedRecords);
  }

  private Result<RequestAndRelatedRecords> sendConfirmationNoticeForRequestWithItemId(
    RequestAndRelatedRecords records) {

    loadMissingLocationDetails(records.getRequest())
      .thenApply(r -> r.next(this::sendConfirmationNoticeForRequestWithItemId));

    return succeeded(records);
  }

  private CompletableFuture<Result<Request>> loadMissingLocationDetails(Request request) {
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

  private Result<Request> sendConfirmationNoticeForRequestWithItemId(Request request) {
    patronNoticeService.acceptNoticeEvent(createPatronNoticeEvent(request, getEventType(request)));
    sendNoticeOnRecall(request);

    return succeeded(request);
  }

  private Result<RequestAndRelatedRecords> sendConfirmationNoticeForRequestWithoutItemId(
    RequestAndRelatedRecords records) {

    return sendNoticeForRequestWithoutItemId(records, getEventType(records.getRequest()),
      TlrSettingsConfiguration::getConfirmationPatronNoticeTemplateId);
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestCancelled(
    RequestAndRelatedRecords relatedRecords) {

    return relatedRecords.getRequest().hasItemId()
      ? sendCancellationNoticeForRequestWithItemId(relatedRecords)
      : sendCancellationNoticeForRequestWithoutItemId(relatedRecords);
  }

  private Result<RequestAndRelatedRecords> sendCancellationNoticeForRequestWithItemId(
    RequestAndRelatedRecords relatedRecords) {

    patronNoticeService.acceptNoticeEvent(
      createPatronNoticeEvent(relatedRecords.getRequest(), REQUEST_CANCELLATION));

    return succeeded(relatedRecords);
  }

  private Result<RequestAndRelatedRecords> sendCancellationNoticeForRequestWithoutItemId(
    RequestAndRelatedRecords records) {

    return sendNoticeForRequestWithoutItemId(records, REQUEST_CANCELLATION,
      TlrSettingsConfiguration::getCancellationPatronNoticeTemplateId);
  }

  private Result<RequestAndRelatedRecords> sendNoticeForRequestWithoutItemId(
    RequestAndRelatedRecords records, NoticeEventType noticeEventType,
    Function<TlrSettingsConfiguration, UUID> templateIdExtractor) {

    Request request = records.getRequest();
    TlrSettingsConfiguration tlrSettings = request.getTlrSettingsConfiguration();
    UUID templateId = templateIdExtractor.apply(tlrSettings);

    if (tlrSettings.isTitleLevelRequestsFeatureEnabled() && templateId != null) {
      PatronNoticeEvent patronNoticeEvent = createPatronNoticeEvent(request, noticeEventType);
      NoticeLogContext noticeLogContext = patronNoticeEvent.getNoticeLogContext()
        .withTriggeringEvent(patronNoticeEvent.getEventType().getRepresentation())
        .withTemplateId(templateId.toString());
      NoticeConfiguration noticeConfiguration = buildTlrNoticeConfiguration(patronNoticeEvent,
        templateId);
      PatronNotice patronNotice = new PatronNotice(patronNoticeEvent.getUser().getId(),
        patronNoticeEvent.getNoticeContext(), noticeConfiguration);

      patronNoticeService.sendNotice(patronNotice, noticeLogContext);
    }

    return succeeded(records);
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestMoved(
    RequestAndRelatedRecords relatedRecords) {

    sendNoticeOnRecall(relatedRecords.getRequest());

    return succeeded(relatedRecords);
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestUpdated(
    RequestAndRelatedRecords relatedRecords) {
    if (relatedRecords.getRequest().getStatus() == RequestStatus.CLOSED_CANCELLED) {
      requestRepository.loadCancellationReason(relatedRecords.getRequest())
        .thenApply(r -> r.map(relatedRecords::withRequest))
        .thenAccept(r -> r.next(this::sendNoticeOnRequestCancelled));
    }
    return succeeded(relatedRecords);
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

  private void fetchDataAndSendRequestAwaitingPickupNotice(Request request) {
    ofAsync(() -> request)
      .thenCompose(r -> r.combineAfter(this::fetchServicePoint, Request::withPickupServicePoint))
      .thenCompose(r -> r.combineAfter(this::fetchRequester, Request::withRequester))
      .thenApply(r -> r.mapFailure(failure -> publishNoticeErrorEvent(failure, request)))
      .thenCompose(r -> r.after(this::sendRequestAwaitingPickupNotice));
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

  private CompletableFuture<Result<Void>> sendRequestAwaitingPickupNotice(Request request) {
    return patronNoticeService.acceptNoticeEvent(
      createPatronNoticeEvent(request, NoticeEventType.AVAILABLE));
  }

  private Result<Request> publishNoticeErrorEvent(HttpFailure failure, Request request) {
    log.error("Failed to send Request Awaiting Pickup notice for request {} to user {}. Cause: {}",
      request.getId(), request.getRequesterId(), failure);

    eventPublisher.publishNoticeErrorLogEvent(NoticeLogContext.from(request), failure);

    return failed(failure);
  }

  private void sendNoticeOnRecall(Request request) {
    Loan loan = request.getLoan();

    if (request.isRecall() && loan != null && loan.getUser() != null && loan.getItem() != null) {
      PatronNoticeEvent itemRecalledEvent = new PatronNoticeEventBuilder()
        .withItem(loan.getItem())
        .withUser(loan.getUser())
        .withEventType(NoticeEventType.ITEM_RECALLED)
        .withNoticeContext(TemplateContextUtil.createLoanNoticeContext(loan))
        .withNoticeLogContext(NoticeLogContext.from(loan))
        .build();
      patronNoticeService.acceptNoticeEvent(itemRecalledEvent);
      eventPublisher.publishRecallRequestedEvent(loan);
    }
  }

  private static PatronNoticeEvent createPatronNoticeEvent(Request request,
    NoticeEventType noticeEventType) {

    Item item = request.getItem();

    return new PatronNoticeEventBuilder()
      .withUser(request.getRequester())
      .withEventType(noticeEventType)
      .withItem(item != null && item.isFound() ? item : null)
      .withNoticeContext(createRequestNoticeContext(request))
      .withNoticeLogContext(NoticeLogContext.from(request))
      .build();
  }

  private NoticeConfiguration buildTlrNoticeConfiguration(PatronNoticeEvent patronNoticeEvent,
    UUID templateId) {

    return new NoticeConfiguration(templateId.toString(), EMAIL,
      patronNoticeEvent.getEventType(), null, null, false, null, true);
  }

  private static NoticeEventType getEventType(Request request) {
    return requestTypeToEventMap.getOrDefault(request.getRequestType(), NoticeEventType.UNKNOWN);
  }

  private Result<Boolean> isNull(Object o) {
    return succeeded(o == null);
  }

}
