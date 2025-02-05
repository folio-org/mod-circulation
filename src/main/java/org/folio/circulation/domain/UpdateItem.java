package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.services.RequestQueueService;
import org.folio.circulation.support.results.Result;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class UpdateItem {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final ItemRepository itemRepository;
  private final RequestQueueService requestQueueService;

  public CompletableFuture<Result<Item>> onCheckIn(Item item, Request request,
    UUID checkInServicePointId, String loggedInUserId, ZonedDateTime dateTime) {

    log.debug("onCheckIn:: parameters item: {}, request: {}, checkInServicePointId: {}, " +
      "loggedInUserId: {}, dateTime: {}", () -> item, () -> request, () -> checkInServicePointId,
      () -> loggedInUserId, () -> dateTime);

    return changeItemOnCheckIn(item, request, checkInServicePointId)
      .next(addLastCheckInProperties(checkInServicePointId, loggedInUserId, dateTime))
      .after(this::storeItem);
  }

  private Function<Item, Result<Item>> addLastCheckInProperties(UUID checkInServicePointId,
    String loggedInUserId, ZonedDateTime dateTime) {

    return item -> succeeded(item.withLastCheckIn(
      new LastCheckIn(dateTime, checkInServicePointId, loggedInUserId)));
  }

  private Result<Item> changeItemOnCheckIn(Item item, Request request, UUID checkInServicePointId) {
    log.debug("changeItemOnCheckIn parameters item: {}, request: {}, checkInServicePointId: {}",
      () -> item, () -> request, () -> checkInServicePointId);
    if (request != null) {
      return changeItemWithOutstandingRequest(item, request, checkInServicePointId);
    } else {
      if(Optional.ofNullable(item.getLocation())
        .map(location ->
          location.homeLocationIsServedBy(checkInServicePointId)
            || (item.canFloatThroughCheckInServicePoint()))
        .orElse(false)) {
        return succeeded(item.available());
      } else {
        return succeeded(item.inTransitToHome());
      }
    }
  }

  private Result<Item> changeItemWithOutstandingRequest(Item item, Request request,
    UUID checkInServicePointId) {

    log.debug("changeItemWithOutstandingRequest:: parameters item: {}, request: {}, " +
      "checkInServicePointId: {}", () -> item, () -> request, () -> checkInServicePointId);
    Result<Item> itemResult;
    switch (request.getfulfillmentPreference()) {
      case HOLD_SHELF:
        itemResult = changeItemWithHoldRequest(item, checkInServicePointId, request);
        break;
      case DELIVERY:
        itemResult = changeItemWithDeliveryRequest(item, request);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + request.getfulfillmentPreference());
    }

    return itemResult;
  }

  private Result<Item> changeItemWithHoldRequest(Item item, UUID checkInServicePointId,
    Request request) {

    String pickupServicePointIdString = request.getPickupServicePointId();

    if (pickupServicePointIdString == null) {
      log.warn("changeItemWithHoldRequest:: pickupServicePointIdString is null");
      return failedValidation(
        "Failed to check in item due to the highest priority " +
          "request missing a pickup service point",
        "pickupServicePointId", null);
    }

    UUID pickUpServicePointId = UUID.fromString(pickupServicePointIdString);
    if (checkInServicePointId.equals(pickUpServicePointId)) {
      return succeeded(item.changeStatus(request.checkedInItemStatus()));
    } else {
      return succeeded(item.inTransitToServicePoint(pickUpServicePointId));
    }
  }

  private Result<Item> changeItemWithDeliveryRequest(Item item, Request request) {
    return succeeded(item.changeStatus(request.checkedInItemStatus()));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> onLoanCreated(
    LoanAndRelatedRecords relatedRecords) {

    log.debug("onLoanCreated:: parameters relatedRecords: {}", () -> relatedRecords);
    //Hack for creating returned loan - should distinguish further up the chain
    return succeeded(relatedRecords).after(when(
      records -> loanIsClosed(relatedRecords), UpdateItem::skip,
      records -> updateItemStatusOnCheckOut(relatedRecords)));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> onLoanUpdate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    log.debug("onLoanUpdate:: parameters loanAndRelatedRecords: {}",
      () -> loanAndRelatedRecords);

    return onLoanUpdate(loanAndRelatedRecords.getLoan(),
      loanAndRelatedRecords.getRequestQueue())
      .thenApply(itemResult -> itemResult.map(loanAndRelatedRecords::withItem));
  }

  public CompletableFuture<Result<Request>> onRequestDeletion(Request request) {
    log.debug("onRequestDeletion:: parameters request: {}", () -> request);
    // Only page request changes item status to 'Paged'
    // Other request types (Hold and Recall) don't change item status, it stays 'Checked out'
    if (request.getRequestType() != null && request.getRequestType().isPage()
      && request.getItem() != null && PAGED.equals(request.getItem().getStatus())) {

      log.info("onRequestDeletion:: updating item with Available status");

      return itemRepository.updateItem(request.getItem().changeStatus(AVAILABLE))
        .thenApply(r -> r.map(item -> request));
    }

    return ofAsync(request);
  }

  private CompletableFuture<Result<Item>> onLoanUpdate(Loan loan, RequestQueue requestQueue) {
    log.debug("onLoanUpdate parameters loan: {}, requestQueue: {}", () -> loan,
      () -> requestQueue);
    return itemStatusOnLoanUpdate(loan, requestQueue)
      .thenCompose(r -> r.after(prospectiveStatus -> updateItemWhenNotSameStatus(prospectiveStatus,
        loan.getItem())));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> onRequestCreateOrUpdate(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("onRequestCreateOrUpdate:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);

    return of(() -> itemStatusOnRequestCreateOrUpdate(requestAndRelatedRecords))
      .after(prospectiveStatus -> updateItemWhenNotSameStatus(prospectiveStatus,
          requestAndRelatedRecords.getRequest().getItem()))
      .thenApply(itemResult -> itemResult.map(requestAndRelatedRecords::withItem));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> updateItemStatusOnCheckOut(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return updateItemWhenNotSameStatus(CHECKED_OUT,
      loanAndRelatedRecords.getLoan().getItem())
      .thenApply(itemResult -> itemResult.map(loanAndRelatedRecords::withItem));
  }

  private CompletableFuture<Result<Item>> updateItemWhenNotSameStatus(
    ItemStatus prospectiveStatus, Item item) {

    if(item.isNotSameStatus(prospectiveStatus)) {
      item.changeStatus(prospectiveStatus);

      return storeItem(item);
    }
    else {
      return completedFuture(succeeded(item));
    }
  }

  private CompletableFuture<Result<Item>> storeItem(Item item) {
    return itemRepository.updateItem(item);
  }

  private CompletableFuture<Result<Boolean>> loanIsClosed(
    LoanAndRelatedRecords relatedRecords) {

    return completedFuture(of(() -> relatedRecords.getLoan().isClosed()));
  }

  private static <T> CompletableFuture<Result<T>> skip(T previousResult) {
    return completedFuture(succeeded(previousResult));
  }

  private ItemStatus itemStatusOnRequestCreateOrUpdate(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    RequestType type = requestAndRelatedRecords.getRequest().getRequestType();

    Item item = requestAndRelatedRecords.getRequest().getItem();

    return (item.isPaged() && queueContainsNoRequestsForItem(requestAndRelatedRecords, item))
      ? AVAILABLE
      : pagedWhenRequested(type, item);
  }

  private boolean queueContainsNoRequestsForItem(RequestAndRelatedRecords requestAndRelatedRecords,
    Item item) {

    RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();

    return requestAndRelatedRecords.isTlrFeatureEnabled()
      ? requestQueue.getRequests().stream().noneMatch(request -> request.isFor(item))
      : requestQueue.isEmpty();
  }

  private ItemStatus pagedWhenRequested(RequestType type, Item item) {
    return (item.isAvailable() && type.isPage())
      ? PAGED
      : item.getStatus();
  }

  private CompletableFuture<Result<ItemStatus>> itemStatusOnLoanUpdate(Loan loan,
    RequestQueue requestQueue) {

    if (loan.isClosed()) {
      return requestQueueService.findRequestFulfillableByItem(loan.getItem(), requestQueue)
        .thenApply(r -> r.map(request -> Optional.ofNullable(request)
          .map(Request::checkedInItemStatus)
          .orElse(AVAILABLE)));
    }
    else if (loan.getItem().isDeclaredLost()) {
      return ofAsync(loan.getItem().getStatus());
    }

    return ofAsync(CHECKED_OUT);
  }

}
