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

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.results.Result;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class UpdateItem {
  private final ItemRepository itemRepository;

  public CompletableFuture<Result<Item>> onCheckIn(Item item, RequestQueue requestQueue,
      UUID checkInServicePointId, String loggedInUserId, ZonedDateTime dateTime) {

    return changeItemOnCheckIn(item, requestQueue, checkInServicePointId)
      .next(addLastCheckInProperties(checkInServicePointId, loggedInUserId, dateTime))
      .after(this::storeItem);
  }

  private Function<Item, Result<Item>> addLastCheckInProperties(
      UUID checkInServicePointId, String loggedInUserId, ZonedDateTime dateTime) {
    return item -> succeeded(item.withLastCheckIn(
      new LastCheckIn(dateTime, checkInServicePointId, loggedInUserId)));
  }

  private Result<Item> changeItemOnCheckIn(Item item, RequestQueue requestQueue,
    UUID checkInServicePointId) {

    if (requestQueue.hasOutstandingRequestsFulfillableByItem(item)) {
      return changeItemWithOutstandingRequest(item, requestQueue, checkInServicePointId);
    } else {
      if(Optional.ofNullable(item.getLocation())
        .map(location -> location.homeLocationIsServedBy(checkInServicePointId))
        .orElse(false)) {
        return succeeded(item.available());
      }
      else {
        return succeeded(item.inTransitToHome());
      }
    }
  }

  private Result<Item> changeItemWithOutstandingRequest(Item item, RequestQueue requestQueue,
    UUID checkInServicePointId) {

    Request req = requestQueue.getHighestPriorityRequestFulfillableByItem(item);

    Result<Item> itemResult;
    switch (req.getFulfilmentPreference()) {
      case HOLD_SHELF:
        itemResult = changeItemWithHoldRequest(item, checkInServicePointId, req);
        break;
      case DELIVERY:
        itemResult = changeItemWithDeliveryRequest(item, req);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " +
          req.getFulfilmentPreference());
    }

    return itemResult;
  }

  private Result<Item> changeItemWithHoldRequest(Item item, UUID checkInServicePointId,
    Request request) {

    String pickupServicePointIdString = request.getPickupServicePointId();

    if (pickupServicePointIdString == null) {
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

    //Hack for creating returned loan - should distinguish further up the chain
    return succeeded(relatedRecords).after(when(
      records -> loanIsClosed(relatedRecords), UpdateItem::skip,
      records -> updateItemStatusOnCheckOut(relatedRecords)));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> onLoanUpdate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return onLoanUpdate(loanAndRelatedRecords.getLoan(),
      loanAndRelatedRecords.getRequestQueue())
      .thenApply(itemResult -> itemResult.map(loanAndRelatedRecords::withItem));
  }

  public CompletableFuture<Result<Request>> onRequestDeletion(Request request) {
    // Only page request changes item status to 'Paged'
    // Other request types (Hold and Recall) don't change item status, it stays 'Checked out'
    if (request.getRequestType() != null && request.getRequestType().isPage()
      && request.getItem() != null && PAGED.equals(request.getItem().getStatus())) {

      return itemRepository.updateItem(request.getItem().changeStatus(AVAILABLE))
        .thenApply(r -> r.map(item -> request));
    }

    return ofAsync(request);
  }

  private CompletableFuture<Result<Item>> onLoanUpdate(
    Loan loan,
    RequestQueue requestQueue) {

    return of(() -> itemStatusOnLoanUpdate(loan, requestQueue))
      .after(prospectiveStatus -> updateItemWhenNotSameStatus(prospectiveStatus,
        loan.getItem()));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> onRequestCreateOrUpdate(
    RequestAndRelatedRecords requestAndRelatedRecords) {

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
    ItemStatus prospectiveStatus,
    Item item) {

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

  private ItemStatus itemStatusOnLoanUpdate(Loan loan, RequestQueue requestQueue) {
    if(loan.isClosed()) {
      return itemStatusOnCheckIn(requestQueue, loan.getItem());
    }
    else if(loan.getItem().isDeclaredLost()) {
      return loan.getItem().getStatus();
    }
    return CHECKED_OUT;
  }

  private ItemStatus itemStatusOnCheckIn(RequestQueue requestQueue, Item item) {
    return requestQueue.checkedInItemStatus(item);
  }
}
