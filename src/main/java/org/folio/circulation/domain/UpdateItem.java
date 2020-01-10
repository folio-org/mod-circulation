package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.http.CommonResponseInterpreters.noContentRecordInterpreter;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.representations.ItemSummaryRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;


public class UpdateItem {

  private final CollectionResourceClient itemsStorageClient;

  public UpdateItem(Clients clients) {
    itemsStorageClient = clients.itemsStorage();
  }

  public CompletableFuture<Result<Item>> onCheckIn(Item item, RequestQueue requestQueue,
      UUID checkInServicePointId, String loggedInUserId, DateTime dateTime) {
    return changeItemOnCheckIn(item, requestQueue, checkInServicePointId)
      .next(addLastCheckInProperties(checkInServicePointId, loggedInUserId, dateTime))
      .after(this::storeItem);
  }

  private Function<Item, Result<Item>> addLastCheckInProperties(
      UUID checkInServicePointId, String loggedInUserId, DateTime dateTime) {
    return item -> succeeded(item.withLastCheckIn(
      new LastCheckIn(dateTime, checkInServicePointId, loggedInUserId)));
  }

  private Result<Item> changeItemOnCheckIn(
    Item item,
    RequestQueue requestQueue,
    UUID checkInServicePointId) {

    if (requestQueue.hasOutstandingFulfillableRequests()) {
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

  private Result<Item> changeItemWithOutstandingRequest(
    Item item,
    RequestQueue requestQueue,
    UUID checkInServicePointId) {

    Request req = requestQueue.getHighestPriorityFulfillableRequest();

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

  private Result<Item> changeItemWithHoldRequest(
    Item item,
    UUID checkInServicePointId,
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

  public CompletableFuture<Result<LoanAndRelatedRecords>> onCheckOut(
    LoanAndRelatedRecords relatedRecords) {

    //Hack for creating returned loan - should distinguish further up the chain
    return succeeded(relatedRecords).afterWhen(
      records -> loanIsClosed(relatedRecords),
      UpdateItem::skip,
      records -> updateItemStatusOnCheckOut(relatedRecords));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> onLoanUpdate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return onLoanUpdate(loanAndRelatedRecords.getLoan(),
      loanAndRelatedRecords.getRequestQueue())
      .thenApply(itemResult -> itemResult.map(loanAndRelatedRecords::withItem));
  }

  public Item onDestinationServicePointUpdate(Item item, ServicePoint servicePoint) {
    return item.updateDestinationServicePoint(servicePoint);
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
    return itemsStorageClient.put(item.getItemId(),
      new ItemSummaryRepresentation().createItemStorageRepresentation(item))
      .thenApply(noContentRecordInterpreter(item)::apply);
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

    RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();

    Item item = requestAndRelatedRecords.getRequest().getItem();

    return (item.getStatus().equals(PAGED) && requestQueue.getRequests().isEmpty())
      ? AVAILABLE
      : (item.getStatus().equals(AVAILABLE) && type.equals(RequestType.PAGE))
        ? PAGED
        : item.getStatus();
  }

  private ItemStatus itemStatusOnLoanUpdate(
    Loan loan,
    RequestQueue requestQueue) {

    if(loan.isClosed()) {
      return itemStatusOnCheckIn(requestQueue);
    }
    else {
      return loan.getItem().getStatus();
    }
  }

  private ItemStatus itemStatusOnCheckIn(RequestQueue requestQueue) {
    return requestQueue.checkedInItemStatus();
  }
}
