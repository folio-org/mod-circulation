package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.runAsync;
import static org.folio.circulation.domain.RequestLevel.TITLE;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createRequestNoticeContext;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
import org.folio.circulation.domain.notice.ImmediatePatronNoticeService;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.SingleImmediatePatronNoticeService;
import org.folio.circulation.domain.notice.TemplateContextUtil;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
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

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestCreated(
    RequestAndRelatedRecords relatedRecords) {

    return sendNoticeOnRequestCreated(relatedRecords);
  }

  protected void sendLogEvent(Loan loan) {
    runAsync(() -> loanRepository.getById(loan.getId())
      .thenAccept(existingLoan -> existingLoan.map(l -> loan.setPreviousDueDate(l.getDueDate())))
      .thenApply(vVoid -> eventPublisher.publishRecallRequestedEvent(loan)));
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestMoved(
    RequestAndRelatedRecords relatedRecords) {

    Request request = relatedRecords.getRequest();

    Loan loan = request.getLoan();
    if (request.getRequestType() == RequestType.RECALL && loan != null) {
      sendNoticeOnItemRecalledEvent(loan);
      sendLogEvent(loan);
    }
    return Result.succeeded(relatedRecords);
  }

  public Result<RequestAndRelatedRecords> sendNoticeOnRequestUpdated(
    RequestAndRelatedRecords relatedRecords) {
    if (relatedRecords.getRequest().getStatus() == RequestStatus.CLOSED_CANCELLED) {
      requestRepository.loadCancellationReason(relatedRecords.getRequest())
        .thenApply(r -> r.map(relatedRecords::withRequest))
        .thenAccept(r -> r.next(this::sendNoticeOnRequestCancelled));
    }
    return Result.succeeded(relatedRecords);
  }

  protected Result<Void> sendNoticeOnRequestCancelled(RequestAndRelatedRecords relatedRecords) {
    return sendNoticeOnRequestCancelled(relatedRecords);
  }

  protected PatronNoticeEvent createPatronNoticeEvent(Request request,
    NoticeEventType eventType) {

    return new PatronNoticeEventBuilder()
      .withUser(request.getRequester())
      .withEventType(eventType)
      .withNoticeContext(createRequestNoticeContext(request))
      .withNoticeLogContext(NoticeLogContext.from(request))
      .build();
  }

  protected Result<Void> sendNoticeOnItemRecalledEvent(Loan loan) {
    if (loan.getUser() != null && loan.getItem() != null) {
      PatronNoticeEvent itemRecalledEvent = new PatronNoticeEventBuilder()
        .withItem(loan.getItem())
        .withUser(loan.getUser())
        .withEventType(NoticeEventType.ITEM_RECALLED)
        .withNoticeContext(TemplateContextUtil.createLoanNoticeContext(loan))
        .withNoticeLogContext(NoticeLogContext.from(loan))
        .build();
      patronNoticeService.acceptNoticeEvent(itemRecalledEvent);
    }
    return Result.succeeded(null);
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

  public CompletableFuture<Result<User>> fetchRequester(Request request) {
    String requesterId = request.getRequesterId();

    return userRepository.getUser(requesterId)
      .thenApply(r -> r.failWhen(this::isNull,
        user -> new RecordNotFoundFailure("user", requesterId)));
  }

  public CompletableFuture<Result<ServicePoint>> fetchServicePoint(Request request) {
    String pickupServicePointId = request.getPickupServicePointId();

    return servicePointRepository.getServicePointById(pickupServicePointId)
      .thenApply(r -> r.failWhen(this::isNull,
        sp -> new RecordNotFoundFailure("servicePoint", pickupServicePointId)));
  }

  private Result<Boolean> isNull(Object o) {
    return succeeded(o == null);
  }

  private CompletableFuture<Result<Void>> sendRequestAwaitingPickupNotice(Request request) {
    Item item = request.getItem();
    User user = request.getRequester();

    PatronNoticeEvent noticeEvent = new PatronNoticeEventBuilder()
      .withItem(item)
      .withUser(user)
      .withEventType(NoticeEventType.AVAILABLE)
      .withNoticeContext(createRequestNoticeContext(request))
      .withNoticeLogContext(NoticeLogContext.from(request))
      .build();

    return patronNoticeService.acceptNoticeEvent(noticeEvent);
  }

  private Result<Request> publishNoticeErrorEvent(HttpFailure failure, Request request) {
    log.error("Failed to send Request Awaiting Pickup notice for request {} to user {}. Cause: {}",
      request.getId(), request.getRequesterId(), failure);

    eventPublisher.publishNoticeErrorLogEvent(NoticeLogContext.from(request), failure);

    return failed(failure);
  }

}


