package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createAvailableNoticeContext;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContext;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.infrastructure.storage.users.AddressTypeRepository;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanCheckInService;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.domain.OverdueFineCalculatorService;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.LogCheckInService;
import org.folio.circulation.services.LostItemFeeRefundService;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.server.WebContext;

class CheckInProcessAdapter {
  private final ItemByBarcodeInStorageFinder itemFinder;
  private final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder;
  private final LoanCheckInService loanCheckInService;
  private final RequestQueueRepository requestQueueRepository;
  private final UpdateItem updateItem;
  private final UpdateRequestQueue requestQueueUpdate;
  private final LoanRepository loanRepository;
  private final ServicePointRepository servicePointRepository;
  private final PatronNoticeService patronNoticeService;
  private final UserRepository userRepository;
  private final AddressTypeRepository addressTypeRepository;
  private final LogCheckInService logCheckInService;
  private final OverdueFineCalculatorService overdueFineCalculatorService;
  private final FeeFineScheduledNoticeService feeFineScheduledNoticeService;
  private final LostItemFeeRefundService lostItemFeeRefundService;

  @SuppressWarnings("squid:S00107")
  CheckInProcessAdapter(
    ItemByBarcodeInStorageFinder itemFinder,
    SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder,
    LoanCheckInService loanCheckInService,
    RequestQueueRepository requestQueueRepository,
    UpdateItem updateItem, UpdateRequestQueue requestQueueUpdate,
    LoanRepository loanRepository, ServicePointRepository servicePointRepository,
    PatronNoticeService patronNoticeService, UserRepository userRepository,
    AddressTypeRepository addressTypeRepository,
    LogCheckInService logCheckInService,
    OverdueFineCalculatorService overdueFineCalculatorService,
    FeeFineScheduledNoticeService feeFineScheduledNoticeService,
    LostItemFeeRefundService lostItemFeeRefundService) {

    this.itemFinder = itemFinder;
    this.singleOpenLoanFinder = singleOpenLoanFinder;
    this.loanCheckInService = loanCheckInService;
    this.requestQueueRepository = requestQueueRepository;
    this.updateItem = updateItem;
    this.requestQueueUpdate = requestQueueUpdate;
    this.loanRepository = loanRepository;
    this.servicePointRepository = servicePointRepository;
    this.patronNoticeService = patronNoticeService;
    this.userRepository = userRepository;
    this.addressTypeRepository = addressTypeRepository;
    this.logCheckInService = logCheckInService;
    this.overdueFineCalculatorService = overdueFineCalculatorService;
    this.feeFineScheduledNoticeService = feeFineScheduledNoticeService;
    this.lostItemFeeRefundService = lostItemFeeRefundService;
  }

  public static CheckInProcessAdapter newInstance(Clients clients, EventPublisher eventPublisher) {
    final LoanRepository loanRepository = new LoanRepository(clients);
    final UserRepository userRepository = new UserRepository(clients);

    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);

    final ItemByBarcodeInStorageFinder itemFinder =
      new ItemByBarcodeInStorageFinder(itemRepository);

    final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder
      = new SingleOpenLoanForItemInStorageFinder(loanRepository, userRepository, true);

    return new CheckInProcessAdapter(itemFinder,
      singleOpenLoanFinder,
      new LoanCheckInService(),
      RequestQueueRepository.using(clients),
      new UpdateItem(clients),
      UpdateRequestQueue.using(clients),
      loanRepository,
      new ServicePointRepository(clients),
      new PatronNoticeService(new PatronNoticePolicyRepository(clients), clients, eventPublisher),
      userRepository,
      new AddressTypeRepository(clients),
      new LogCheckInService(clients),
      OverdueFineCalculatorService.using(clients),
      FeeFineScheduledNoticeService.using(clients),
      new LostItemFeeRefundService(clients));
  }

  CompletableFuture<Result<Item>> findItem(CheckInContext context) {
    return itemFinder.findItemByBarcode(context.getCheckInRequestBarcode());
  }

  CompletableFuture<Result<Loan>> findSingleOpenLoan(
    CheckInContext context) {

    return singleOpenLoanFinder.findSingleOpenLoan(context.getItem());
  }

  CompletableFuture<Result<Loan>> checkInLoan(CheckInContext context) {
    return completedFuture(
      loanCheckInService.checkIn(context.getLoan(), context.getCheckInProcessedDateTime(),
        context.getCheckInRequest()));
  }

  CompletableFuture<Result<RequestQueue>> getRequestQueue(
    CheckInContext context) {

    return requestQueueRepository.get(context.getItem().getItemId());
  }

  CompletableFuture<Result<Item>> updateItem(CheckInContext context) {
    return updateItem.onCheckIn(context.getItem(), context.getRequestQueue(),
      context.getCheckInServicePointId(), context.getLoggedInUserId(),
     context.getCheckInProcessedDateTime());
  }

  CompletableFuture<Result<RequestQueue>> updateRequestQueue(
    CheckInContext context) {

    return requestQueueUpdate.onCheckIn(context.getRequestQueue(),
      context.getCheckInServicePointId().toString());
  }

  CompletableFuture<Result<Loan>> updateLoan(CheckInContext context) {
    // Loan must be updated after item
    // due to snapshot of item status stored with the loan
    // as this is how the loan action history is populated
    return loanRepository.updateLoan(context.getLoan());
  }

  CompletableFuture<Result<Item>> getDestinationServicePoint(CheckInContext context) {
    final Item item = context.getItem();

    if (item.getInTransitDestinationServicePointId() != null && item.getInTransitDestinationServicePoint() == null) {
      final UUID inTransitDestinationServicePointId = UUID.fromString(item.getInTransitDestinationServicePointId());
      return servicePointRepository.getServicePointById(inTransitDestinationServicePointId)
        .thenCompose(result ->
          result.after(servicePoint ->
            completedFuture(succeeded(updateItem.onDestinationServicePointUpdate(item, servicePoint))))
        );
    }

    return completedFuture(succeeded(item));
  }

  CompletableFuture<Result<ServicePoint>> getCheckInServicePoint(CheckInContext context) {
    return servicePointRepository.getServicePointById(context.getCheckInServicePointId());
  }

  CompletableFuture<Result<Request>> getPickupServicePoint(CheckInContext context) {
    Request firstRequest = context.getHighestPriorityFulfillableRequest();
    if (firstRequest == null) {
      return completedFuture(succeeded(null));
    }
    return StringUtils.isNotBlank(firstRequest.getPickupServicePointId())
      ? servicePointRepository.getServicePointById(UUID.fromString(firstRequest.getPickupServicePointId()))
          .thenApply(r -> r.map(firstRequest::withPickupServicePoint))
      : completedFuture(succeeded(firstRequest));
  }

  CompletableFuture<Result<Request>> getRequester(CheckInContext context) {
    Request firstRequest = context.getHighestPriorityFulfillableRequest();
    if (firstRequest == null) {
      return completedFuture(succeeded(null));
    }
    return userRepository.getUser(firstRequest)
      .thenApply(r -> r.map(firstRequest::withRequester));
  }

  CompletableFuture<Result<Request>> getAddressType(CheckInContext context) {
    Request firstRequest = context.getHighestPriorityFulfillableRequest();
    if (firstRequest == null) {
      return completedFuture(succeeded(null));
    }
    return addressTypeRepository.getAddressTypeById(firstRequest.getDeliveryAddressTypeId())
      .thenApply(r -> r.map(firstRequest::withAddressType));
  }

  Result<CheckInContext> sendCheckInPatronNotice(CheckInContext context) {
    if (context.getLoan() == null) {
      return succeeded(context);
    }
    PatronNoticeEvent noticeEvent = new PatronNoticeEventBuilder()
      .withItem(context.getItem())
      .withUser(context.getLoan().getUser())
      .withEventType(NoticeEventType.CHECK_IN)
      .withNoticeContext(createLoanNoticeContext(context.getLoan()))
      .build();
    patronNoticeService.acceptNoticeEvent(noticeEvent, new NoticeLogContext());
    return succeeded(context);
  }

  Result<CheckInContext> sendItemStatusPatronNotice(CheckInContext context) {
    RequestQueue requestQueue = context.getRequestQueue();
    if (Objects.isNull(requestQueue)) {
      return succeeded(context);
    }

    requestQueue.getRequests().stream()
      .findFirst()
      .ifPresent(firstRequest -> sendAvailableNotice(context, firstRequest));
    return succeeded(context);
  }

  private void sendAvailableNotice(CheckInContext context, Request firstRequest) {
    servicePointRepository.getServicePointForRequest(firstRequest)
      .thenApply(r -> r.map(firstRequest::withPickupServicePoint))
      .thenCombine(userRepository.getUserByBarcode(firstRequest.getRequesterBarcode()),
        (requestResult, userResult) -> requestResult.combine(userResult,
          (request, user) -> sendAvailableNotice(request, user, context)));
  }

  private Result<CheckInContext> sendAvailableNotice(Request request, User user, CheckInContext context) {
    Item item = context.getItem();
    if (item.isAwaitingPickup() && item.hasChanged()) {
      PatronNoticeEvent noticeEvent = new PatronNoticeEventBuilder()
        .withItem(item)
        .withUser(user)
        .withEventType(NoticeEventType.AVAILABLE)
        .withNoticeContext(createAvailableNoticeContext(item, user, request))
        .build();
      patronNoticeService.acceptNoticeEvent(noticeEvent, NoticeLogContext.from(item, user, request));
    }
    return succeeded(context);
  }

  CheckInContext setInHouseUse(CheckInContext checkInContext) {
    return checkInContext
      .withInHouseUse(loanCheckInService.isInHouseUse(
        checkInContext.getItem(),
        checkInContext.getRequestQueue(),
        checkInContext.getCheckInRequest()));
  }

  public CompletableFuture<Result<CheckInContext>> logCheckInOperation(
    CheckInContext checkInContext) {

    return logCheckInService.logCheckInOperation(checkInContext);
  }

  CompletableFuture<Result<CheckInContext>> createOverdueFineIfNecessary(
    CheckInContext records, WebContext context) {

    return overdueFineCalculatorService.createOverdueFineIfNecessary(records, context.getUserId())
      .thenApply(r -> r.next(action -> feeFineScheduledNoticeService.scheduleNotices(records, action)));
  }

  CompletableFuture<Result<CheckInContext>> refundLostItemFees(CheckInContext context) {
    return lostItemFeeRefundService.refundLostItemFees(context);
  }
}
