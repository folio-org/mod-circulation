package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.of;
import static org.folio.circulation.support.HttpResult.succeeded;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;

public class UpdateItem {

  private final CollectionResourceClient itemsStorageClient;

  public UpdateItem(Clients clients) {
    itemsStorageClient = clients.itemsStorage();
  }

  public CompletableFuture<HttpResult<Item>> onCheckIn(
    Item item,
    RequestQueue requestQueue,
    UUID checkInServicePointId) {

    return of(() -> changeItemOnCheckIn(item, requestQueue, checkInServicePointId))
      .after(updatedItem -> {
        if(updatedItem.hasChanged()) {
          return storeItem(updatedItem);
        }
        else {
          return completedFuture(succeeded(item));
        }
      });
  }

  private Item changeItemOnCheckIn(
    Item item,
    RequestQueue requestQueue,
    UUID checkInServicePointId) {

    if (requestQueue.hasOutstandingFulfillableRequests()) {
      Request request = requestQueue.getHighestPriorityFulfillableRequest();
      UUID pickUpServicePointId = UUID.fromString(request.getPickupServicePointId());
      if (checkInServicePointId.equals(pickUpServicePointId)) {
        return item.changeStatus(requestQueue.getHighestPriorityFulfillableRequest()
          .checkedInItemStatus());
      } else {
        return item.inTransitToServicePoint(pickUpServicePointId);
      }
    } else {
      if(item.homeLocationIsServedBy(checkInServicePointId)) {
        return item.available();
      }
      else {
        return item.inTransitToHome();
      }
    }
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCheckOut(
    LoanAndRelatedRecords relatedRecords) {

    RequestQueue requestQueue = relatedRecords.getRequestQueue();

    //Hack for creating returned loan - should distinguish further up the chain
    return succeeded(relatedRecords).afterWhen(
      records -> loanIsClosed(relatedRecords),
      UpdateItem::skip,
      records -> updateItemStatusOnCheckOut(relatedRecords, requestQueue));
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onLoanUpdate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return onLoanUpdate(loanAndRelatedRecords.getLoan(),
      loanAndRelatedRecords.getRequestQueue())
      .thenApply(itemResult -> itemResult.map(loanAndRelatedRecords::withItem));
  }

  public Item onDestinationServicePointUpdate(Item item, ServicePoint servicePoint) {
    return item.updateDestinationServicePoint(servicePoint);
  }

  private CompletableFuture<HttpResult<Item>> onLoanUpdate(
    Loan loan,
    RequestQueue requestQueue) {

    return of(() -> itemStatusOnLoanUpdate(loan, requestQueue))
      .after(prospectiveStatus -> updateItemWhenNotSameStatus(prospectiveStatus,
        loan.getItem()));
  }

  CompletableFuture<HttpResult<RequestAndRelatedRecords>> onRequestCreation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return of(() -> itemStatusOnRequestCreation(requestAndRelatedRecords))
      .after(prospectiveStatus -> updateItemWhenNotSameStatus(prospectiveStatus,
          requestAndRelatedRecords.getRequest().getItem()))
      .thenApply(itemResult -> itemResult.map(requestAndRelatedRecords::withItem));
  }

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> updateItemStatusOnCheckOut(
    LoanAndRelatedRecords loanAndRelatedRecords,
    RequestQueue requestQueue) {

    return of(requestQueue::checkedOutItemStatus)
      .after(prospectiveStatus -> updateItemWhenNotSameStatus(prospectiveStatus,
          loanAndRelatedRecords.getLoan().getItem()))
      .thenApply(itemResult -> itemResult.map(loanAndRelatedRecords::withItem));
  }

  private CompletableFuture<HttpResult<Item>> updateItemWhenNotSameStatus(
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

  private CompletableFuture<HttpResult<Item>> storeItem(Item item) {
    return itemsStorageClient.put(item.getItemId(), item.getItem())
      .thenApply(putItemResponse -> {
        if(putItemResponse.getStatusCode() == 204) {
          return succeeded(item);
        }
        else {
          return failed(new ServerErrorFailure(
            String.format("Failed to update item status '%s'",
              putItemResponse.getBody())));
        }
      });
  }

  private CompletableFuture<HttpResult<Boolean>> loanIsClosed(
    LoanAndRelatedRecords relatedRecords) {

    return completedFuture(of(() -> relatedRecords.getLoan().isClosed()));
  }

  private static <T> CompletableFuture<HttpResult<T>> skip(T previousResult) {
    return completedFuture(succeeded(previousResult));
  }

  private ItemStatus itemStatusOnRequestCreation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    RequestType type = requestAndRelatedRecords.getRequest().getRequestType();

    return type == RequestType.PAGE
      ? ItemStatus.PAGED
      : requestAndRelatedRecords.getRequest().getItem().getStatus();
  }

  private ItemStatus itemStatusOnLoanUpdate(
    Loan loan,
    RequestQueue requestQueue) {

    return loan.isClosed()
      ? itemStatusOnCheckIn(requestQueue)
      : requestQueue.checkedOutItemStatus();
  }

  private ItemStatus itemStatusOnCheckIn(RequestQueue requestQueue) {
    return requestQueue.checkedInItemStatus();
  }
}
