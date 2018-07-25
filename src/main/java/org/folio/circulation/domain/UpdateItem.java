package org.folio.circulation.domain;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.support.HttpResult.*;

public class UpdateItem {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient itemsStorageClient;

  public UpdateItem(Clients clients) {
    itemsStorageClient = clients.itemsStorage();
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

    return HttpResult.of(() -> itemStatusOnLoanUpdate(
        loanAndRelatedRecords.getLoan(), loanAndRelatedRecords.getRequestQueue()))
      .after(prospectiveStatus ->
        updateWhenNotSameStatus(loanAndRelatedRecords, prospectiveStatus));
  }

  CompletableFuture<HttpResult<RequestAndRelatedRecords>> onRequestCreation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return HttpResult.of(() -> itemStatusOnRequestCreation(requestAndRelatedRecords))
      .after(prospectiveStatus ->
        updateWhenNotSameStatus(requestAndRelatedRecords, prospectiveStatus));
  }

  private ItemStatus itemStatusOnRequestCreation(RequestAndRelatedRecords requestAndRelatedRecords) {
    RequestType requestType = RequestType.from(requestAndRelatedRecords.getRequest());

    RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();

    return requestQueue.hasOutstandingRequests()
      ? RequestType.from(requestQueue.getHighestPriorityRequest())
      .toCheckedOutItemStatus()
      : requestType.toCheckedOutItemStatus();
  }

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> updateItemStatusOnCheckOut(
    LoanAndRelatedRecords relatedRecords,
    RequestQueue requestQueue) {

    return HttpResult.of(() -> checkOutProspectiveStatusFrom(requestQueue))
      .after(prospectiveStatus ->
        updateWhenNotSameStatus(relatedRecords, prospectiveStatus));
  }

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> updateWhenNotSameStatus(
    LoanAndRelatedRecords relatedRecords,
    ItemStatus prospectiveStatus) {

    final Item item = relatedRecords.getLoan().getItem();

    if(item.isNotSameStatus(prospectiveStatus)) {
      return internalUpdate(item, prospectiveStatus)
        .thenApply(result -> result.map(v -> relatedRecords));
    }
    else {
      return skip(relatedRecords);
    }
  }

  private CompletableFuture<HttpResult<RequestAndRelatedRecords>> updateWhenNotSameStatus(
    RequestAndRelatedRecords relatedRecords,
    ItemStatus prospectiveStatus) {

    final Item item = relatedRecords.getRequest().getItem();

    if(item.isNotSameStatus(prospectiveStatus)) {
      return internalUpdate(item, prospectiveStatus)
        .thenApply(result -> result.map(v -> relatedRecords));
    }
    else {
      return skip(relatedRecords);
    }
  }

  private CompletableFuture<HttpResult<Boolean>> loanIsClosed(
    LoanAndRelatedRecords relatedRecords) {

    return completedFuture(of(() -> relatedRecords.getLoan().isClosed()));
  }

  private CompletableFuture<HttpResult<Item>> internalUpdate(
    Item item,
    ItemStatus newStatus) {

    item.changeStatus(newStatus);

    return this.itemsStorageClient.put(item.getItemId(),
      item.getItem()).thenApply(putItemResponse -> {
        if(putItemResponse.getStatusCode() == 204) {
          return succeeded(item);
        }
        else {
          return failed(new ServerErrorFailure("Failed to update item"));
        }
      });
  }

  private static <T> CompletableFuture<HttpResult<T>> skip(T previousResult) {
    return completedFuture(succeeded(previousResult));
  }

  private ItemStatus checkOutProspectiveStatusFrom(RequestQueue requestQueue) {
    if(requestQueue != null) {
      return requestQueue.hasOutstandingRequests()
        ? RequestType.from(requestQueue.getHighestPriorityRequest()).toCheckedOutItemStatus()
        : CHECKED_OUT;
    }
    else {
      return CHECKED_OUT;
    }
  }

  private ItemStatus itemStatusOnLoanUpdate(Loan loan, RequestQueue requestQueue) {
    if(loan.isClosed()) {
      return requestQueue.hasOutstandingFulfillableRequests()
        ? RequestFulfilmentPreference.from(
          requestQueue.getHighestPriorityFulfillableRequest())
        .toCheckedInItemStatus()
        : AVAILABLE;
    }
    else {
      return requestQueue.hasOutstandingRequests()
        ? RequestType.from(requestQueue.getHighestPriorityRequest())
          .toCheckedOutItemStatus()
        : CHECKED_OUT;
    }
  }

  private void logException(Exception ex) {
    log.error("Exception occurred whilst updating item", ex);
  }
}
