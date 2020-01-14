package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createAvailableNoticeContext;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContext;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.AddressTypeRepository;
import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanCheckInService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.Result;

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

  @SuppressWarnings("squid:S00107")
  CheckInProcessAdapter(
    ItemByBarcodeInStorageFinder itemFinder,
    SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder,
    LoanCheckInService loanCheckInService,
    RequestQueueRepository requestQueueRepository,
    UpdateItem updateItem, UpdateRequestQueue requestQueueUpdate,
    LoanRepository loanRepository, ServicePointRepository servicePointRepository,
    PatronNoticeService patronNoticeService, UserRepository userRepository, AddressTypeRepository addressTypeRepository) {

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
  }

  CompletableFuture<Result<Item>> findItem(CheckInProcessRecords records) {
    return itemFinder.findItemByBarcode(records.getCheckInRequestBarcode());
  }

  CompletableFuture<Result<Loan>> findSingleOpenLoan(
    CheckInProcessRecords records) {

    return singleOpenLoanFinder.findSingleOpenLoan(records.getItem());
  }

  CompletableFuture<Result<Loan>> checkInLoan(CheckInProcessRecords records) {
    return completedFuture(
      loanCheckInService.checkIn(records.getLoan(), records.getCheckInProcessedDateTime(),
        records.getCheckInRequest()));
  }

  CompletableFuture<Result<RequestQueue>> getRequestQueue(
    CheckInProcessRecords records) {

    return requestQueueRepository.get(records.getItem().getItemId());
  }

  CompletableFuture<Result<Item>> updateItem(CheckInProcessRecords records) {
    return updateItem.onCheckIn(records.getItem(), records.getRequestQueue(),
      records.getCheckInServicePointId(), records.getLoggedInUserId(),
     records.getCheckInProcessedDateTime());
  }

  CompletableFuture<Result<RequestQueue>> updateRequestQueue(
    CheckInProcessRecords records) {

    return requestQueueUpdate.onCheckIn(records.getRequestQueue(),
      records.getCheckInServicePointId().toString());
  }

  CompletableFuture<Result<Loan>> updateLoan(CheckInProcessRecords records) {
    // Loan must be updated after item
    // due to snapshot of item status stored with the loan
    // as this is how the loan action history is populated
    return loanRepository.updateLoan(records.getLoan());
  }

  CompletableFuture<Result<Item>> getDestinationServicePoint(CheckInProcessRecords records) {
    final Item item = records.getItem();

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

  CompletableFuture<Result<ServicePoint>> getCheckInServicePoint(CheckInProcessRecords records) {
    return servicePointRepository.getServicePointById(records.getCheckInServicePointId());
  }

  CompletableFuture<Result<Request>> getPickupServicePoint(CheckInProcessRecords records) {
    Request firstRequest = records.getHighestPriorityFulfillableRequest();
    if (firstRequest == null) {
      return completedFuture(succeeded(null));
    }
    return StringUtils.isNotBlank(firstRequest.getPickupServicePointId())
      ? servicePointRepository.getServicePointById(UUID.fromString(firstRequest.getPickupServicePointId()))
          .thenApply(r -> r.map(firstRequest::withPickupServicePoint))
      : completedFuture(succeeded(firstRequest));
  }

  CompletableFuture<Result<Request>> getRequester(CheckInProcessRecords records) {
    Request firstRequest = records.getHighestPriorityFulfillableRequest();
    if (firstRequest == null) {
      return completedFuture(succeeded(null));
    }
    return userRepository.getUser(firstRequest)
      .thenApply(r -> r.map(firstRequest::withRequester));
  }

  CompletableFuture<Result<Request>> getAddressType(CheckInProcessRecords records) {
    Request firstRequest = records.getHighestPriorityFulfillableRequest();
    if (firstRequest == null) {
      return completedFuture(succeeded(null));
    }
    return addressTypeRepository.getAddressTypeById(firstRequest.getDeliveryAddressTypeId())
      .thenApply(r -> r.map(firstRequest::withAddressType));
  }

  Result<CheckInProcessRecords> sendCheckInPatronNotice(CheckInProcessRecords records) {
    if (records.getLoan() == null) {
      return succeeded(records);
    }
    PatronNoticeEvent noticeEvent = new PatronNoticeEventBuilder()
      .withItem(records.getItem())
      .withUser(records.getLoan().getUser())
      .withEventType(NoticeEventType.CHECK_IN)
      .withNoticeContext(createLoanNoticeContext(records.getLoan()))
      .build();
    patronNoticeService.acceptNoticeEvent(noticeEvent);
    return succeeded(records);
  }

  Result<CheckInProcessRecords> sendItemStatusPatronNotice(CheckInProcessRecords records) {
    RequestQueue requestQueue = records.getRequestQueue();
    if (Objects.isNull(requestQueue)) {
      return succeeded(records);
    }

    requestQueue.getRequests().stream()
      .findFirst()
      .ifPresent(firstRequest -> sendAvailableNotice(records, firstRequest));
    return succeeded(records);
  }

  private void sendAvailableNotice(CheckInProcessRecords records, Request firstRequest) {
    servicePointRepository.getServicePointForRequest(firstRequest)
      .thenApply(r -> r.map(firstRequest::withPickupServicePoint))
      .thenCombine(userRepository.getUserByBarcode(firstRequest.getRequesterBarcode()),
        (requestResult, userResult) -> Result.combine(requestResult, userResult,
          (request, user) -> sendAvailableNotice(request, user, records)));
  }

  private Result<CheckInProcessRecords> sendAvailableNotice(Request request, User user, CheckInProcessRecords records) {
    Item item = records.getItem();
    if (item.isAwaitingPickup() && item.hasChanged()) {
      PatronNoticeEvent noticeEvent = new PatronNoticeEventBuilder()
        .withItem(item)
        .withUser(user)
        .withEventType(NoticeEventType.AVAILABLE)
        .withNoticeContext(createAvailableNoticeContext(item, user, request))
        .build();
      patronNoticeService.acceptNoticeEvent(noticeEvent);
    }
    return succeeded(records);
  }

  public CheckInProcessRecords setInHouseUse(CheckInProcessRecords checkInProcessRecords) {
    return checkInProcessRecords
      .withInHouseUse(isInHouseUse(checkInProcessRecords));
  }

  private boolean isInHouseUse(CheckInProcessRecords records) {
    if (records.getItem() == null || records.getItem().isNotFound()) {
      return false;
    }

    if (records.getItem().getLocation() == null) {
      return false;
    }

    return records.getItem().isAvailable()
      && (records.getRequestQueue() == null || records.getRequestQueue().size() == 0)
      && records.getItem().getLocation().homeLocationIsServedBy(records.getCheckInServicePointId());
  }
}
