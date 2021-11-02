package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanCheckInService;
import org.folio.circulation.domain.OverdueFineService;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.users.AddressTypeRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.LogCheckInService;
import org.folio.circulation.services.LostItemFeeRefundService;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

class CheckInProcessAdapter {
  private final ItemByBarcodeInStorageFinder itemFinder;
  private final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder;
  private final LoanCheckInService loanCheckInService;
  private final RequestQueueRepository requestQueueRepository;
  private final UpdateItem updateItem;
  private final UpdateRequestQueue requestQueueUpdate;
  private final LoanRepository loanRepository;
  private final ServicePointRepository servicePointRepository;
  private final UserRepository userRepository;
  private final AddressTypeRepository addressTypeRepository;
  private final LogCheckInService logCheckInService;
  private final OverdueFineService overdueFineService;
  private final FeeFineScheduledNoticeService feeFineScheduledNoticeService;
  private final LostItemFeeRefundService lostItemFeeRefundService;
  protected final EventPublisher eventPublisher;

  @SuppressWarnings("squid:S00107")
  CheckInProcessAdapter(
    ItemByBarcodeInStorageFinder itemFinder,
    SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder,
    LoanCheckInService loanCheckInService,
    RequestQueueRepository requestQueueRepository,
    UpdateItem updateItem, UpdateRequestQueue requestQueueUpdate,
    LoanRepository loanRepository, ServicePointRepository servicePointRepository,
    UserRepository userRepository,
    AddressTypeRepository addressTypeRepository,
    LogCheckInService logCheckInService,
    OverdueFineService overdueFineService,
    FeeFineScheduledNoticeService feeFineScheduledNoticeService,
    LostItemFeeRefundService lostItemFeeRefundService,
    EventPublisher eventPublisher) {

    this.itemFinder = itemFinder;
    this.singleOpenLoanFinder = singleOpenLoanFinder;
    this.loanCheckInService = loanCheckInService;
    this.requestQueueRepository = requestQueueRepository;
    this.updateItem = updateItem;
    this.requestQueueUpdate = requestQueueUpdate;
    this.loanRepository = loanRepository;
    this.servicePointRepository = servicePointRepository;
    this.userRepository = userRepository;
    this.addressTypeRepository = addressTypeRepository;
    this.logCheckInService = logCheckInService;
    this.overdueFineService = overdueFineService;
    this.feeFineScheduledNoticeService = feeFineScheduledNoticeService;
    this.lostItemFeeRefundService = lostItemFeeRefundService;
    this.eventPublisher = eventPublisher;
  }

  public static CheckInProcessAdapter newInstance(Clients clients) {
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
      userRepository,
      new AddressTypeRepository(clients),
      new LogCheckInService(clients),
      OverdueFineService.using(clients),
      FeeFineScheduledNoticeService.using(clients),
      new LostItemFeeRefundService(clients),
      new EventPublisher(clients.pubSubPublishingService()));
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

    return requestQueueRepository.get(context.getItem().getInstanceId());
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

    return overdueFineService.createOverdueFineIfNecessary(records, context.getUserId())
      .thenApply(r -> r.next(action -> feeFineScheduledNoticeService.scheduleOverdueFineNotices(records, action)));
  }

  CompletableFuture<Result<CheckInContext>> refundLostItemFees(CheckInContext context) {
    return lostItemFeeRefundService.refundLostItemFees(context);
  }
}
