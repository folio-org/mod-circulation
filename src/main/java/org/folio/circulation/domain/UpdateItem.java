package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;

public class UpdateItem {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient itemsStorageClient;

  public UpdateItem(Clients clients) {
    itemsStorageClient = clients.itemsStorage();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCheckOut(
    LoanAndRelatedRecords relatedRecords) {

    try {
      RequestQueue requestQueue = relatedRecords.getRequestQueue();

      //Hack for creating returned loan - should distinguish further up the chain
      if(relatedRecords.getLoan().isClosed()) {
        return skip(relatedRecords);
      }

      final String prospectiveStatus;

      if(requestQueue != null) {
        prospectiveStatus = requestQueue.hasOutstandingRequests()
          ? RequestType.from(requestQueue.getHighestPriorityRequest()).toCheckedOutItemStatus()
          : CHECKED_OUT;
      }
      else {
        prospectiveStatus = CHECKED_OUT;
      }

      if(relatedRecords.getLoan().getItem().isNotSameStatus(prospectiveStatus)) {
        return internalUpdate(relatedRecords.getLoan().getItem(), prospectiveStatus)
          .thenApply(updatedItemResult -> updatedItemResult.map(
            relatedRecords::withItem));
      }
      else {
        return skip(relatedRecords);
      }
    }
    catch (Exception ex) {
      logException(ex);
      return CompletableFuture.completedFuture(
        failed(new ServerErrorFailure(ex)));
    }
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onLoanUpdate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    try {
      final Item item = loanAndRelatedRecords.getLoan().getItem();

      final String prospectiveStatus = itemStatusFrom(
        loanAndRelatedRecords.getLoan(), loanAndRelatedRecords.getRequestQueue());

      if(item.isNotSameStatus(prospectiveStatus)) {
        return internalUpdate(item, prospectiveStatus)
          .thenApply(updatedItemResult ->
            updatedItemResult.map(loanAndRelatedRecords::withItem));
      }
      else {
        return skip(loanAndRelatedRecords);
      }
    }
    catch (Exception ex) {
      logException(ex);
      return CompletableFuture.completedFuture(
        failed(new ServerErrorFailure(ex)));
    }
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> onRequestCreation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    try {
      RequestType requestType = RequestType.from(requestAndRelatedRecords.getRequest());

      RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();

      String newStatus = requestQueue.hasOutstandingRequests()
        ? RequestType.from(requestQueue.getHighestPriorityRequest()).toCheckedOutItemStatus()
        : requestType.toCheckedOutItemStatus();

      final Item item = requestAndRelatedRecords.getRequest().getItem();

      if (item.isNotSameStatus(newStatus)) {
        return internalUpdate(item, newStatus)
          .thenApply(updatedItemResult ->
            updatedItemResult.map(requestAndRelatedRecords::withItem));
      } else {
        return skip(requestAndRelatedRecords);
      }
    }
    catch (Exception ex) {
      logException(ex);
      return CompletableFuture.completedFuture(
        failed(new ServerErrorFailure(ex)));
    }
  }

  private CompletableFuture<HttpResult<JsonObject>> internalUpdate(
    Item item,
    String newStatus) {

    item.changeStatus(newStatus);

    return this.itemsStorageClient.put(item.getItemId(),
      item.getItem()).thenApply(putItemResponse -> {
        if(putItemResponse.getStatusCode() == 204) {
          return succeeded(item.getItem());
        }
        else {
          return failed(new ServerErrorFailure("Failed to update item"));
        }
      });
  }

  private <T> CompletableFuture<HttpResult<T>> skip(T previousResult) {
    return CompletableFuture.completedFuture(succeeded(previousResult));
  }

  private String itemStatusFrom(Loan loan, RequestQueue requestQueue) {
    String prospectiveStatus;

    if(loan.isClosed()) {
      prospectiveStatus = requestQueue.hasOutstandingFulfillableRequests()
        ? RequestFulfilmentPreference.from(
          requestQueue.getHighestPriorityFulfillableRequest())
        .toCheckedInItemStatus()
        : AVAILABLE;
    }
    else {
      prospectiveStatus = requestQueue.hasOutstandingRequests()
        ? RequestType.from(requestQueue.getHighestPriorityRequest()).toCheckedOutItemStatus()
        : CHECKED_OUT;
    }

    return prospectiveStatus;
  }

  private void logException(Exception ex) {
    log.error("Exception occurred whilst updating item", ex);
  }
}
