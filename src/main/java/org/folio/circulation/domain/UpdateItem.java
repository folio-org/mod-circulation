package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;

public class UpdateItem {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient itemsStorageClient;

  public UpdateItem(Clients clients) {
    itemsStorageClient = clients.itemsStorage();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCheckOut(
    LoanAndRelatedRecords relatedRecords) {

    try {
      JsonObject item = relatedRecords.inventoryRecords.getItem();
      RequestQueue requestQueue = relatedRecords.requestQueue;

      //Hack for creating returned loan - should distinguish further up the chain
      if(relatedRecords.loan.isClosed()) {
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

      if(isNotSameStatus(item, prospectiveStatus)) {
        return internalUpdate(item, prospectiveStatus)
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
        HttpResult.failure(new ServerErrorFailure(ex)));
    }
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onLoanUpdate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    try {
      JsonObject item = loanAndRelatedRecords.inventoryRecords.getItem();

      final String prospectiveStatus = itemStatusFrom(
        loanAndRelatedRecords.loan, loanAndRelatedRecords.requestQueue);

      if(isNotSameStatus(item, prospectiveStatus)) {
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
        HttpResult.failure(new ServerErrorFailure(ex)));
    }
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> onRequestCreation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    try {
      RequestType requestType = RequestType.from(requestAndRelatedRecords.request);

      RequestQueue requestQueue = requestAndRelatedRecords.requestQueue;

      String newStatus = requestQueue.hasOutstandingRequests()
        ? RequestType.from(requestQueue.getHighestPriorityRequest()).toCheckedOutItemStatus()
        : requestType.toCheckedOutItemStatus();

      if (isNotSameStatus(requestAndRelatedRecords.inventoryRecords.item, newStatus)) {
        return internalUpdate(requestAndRelatedRecords.inventoryRecords.item, newStatus)
          .thenApply(updatedItemResult ->
            updatedItemResult.map(requestAndRelatedRecords::withItem));
      } else {
        return skip(requestAndRelatedRecords);
      }
    }
    catch (Exception ex) {
      logException(ex);
      return CompletableFuture.completedFuture(
        HttpResult.failure(new ServerErrorFailure(ex)));
    }
  }

  private CompletableFuture<HttpResult<JsonObject>> internalUpdate(
    JsonObject item,
    String newStatus) {

    CompletableFuture<HttpResult<JsonObject>> itemUpdated = new CompletableFuture<>();

    item.put("status", new JsonObject().put("name", newStatus));

    this.itemsStorageClient.put(item.getString("id"),
      item, putItemResponse -> {
        if(putItemResponse.getStatusCode() == 204) {
          itemUpdated.complete(HttpResult.success(item));
        }
        else {
          itemUpdated.complete(HttpResult.failure(
            new ServerErrorFailure("Failed to update item")));
        }
      });

    return itemUpdated;
  }

  private static boolean isNotSameStatus(
    JsonObject item,
    String prospectiveStatus) {

    return isNotSameStatus(ItemStatus.getStatus(item), prospectiveStatus);
  }

  private static boolean isNotSameStatus(
    String currentStatus,
    String prospectiveStatus) {

    return !StringUtils.equals(currentStatus, prospectiveStatus);
  }

  private <T> CompletableFuture<HttpResult<T>> skip(T previousResult) {
    return CompletableFuture.completedFuture(HttpResult.success(previousResult));
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
