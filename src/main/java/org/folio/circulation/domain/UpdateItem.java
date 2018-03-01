package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;

public class UpdateItem {
  private final CollectionResourceClient itemsStorageClient;

  public UpdateItem(Clients clients) {
    itemsStorageClient = clients.itemsStorage();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCheckOut(
    LoanAndRelatedRecords relatedRecords) {

    JsonObject item = relatedRecords.inventoryRecords.getItem();
    String prospectiveStatus = itemStatusFrom(relatedRecords.loan);

    if(isNotSameStatusIgnoringCheckOutVariants(item, prospectiveStatus)) {
      return internalUpdate(item, prospectiveStatus)
        .thenApply(updatedItemResult -> updatedItemResult.map(
          relatedRecords::withItem));
    }
    else {
      return skip(relatedRecords);
    }
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onLoanUpdate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    String prospectiveStatus = itemStatusFrom(loanAndRelatedRecords.loan);
    JsonObject item = loanAndRelatedRecords.inventoryRecords.getItem();

    if(isNotSameStatusIgnoringCheckOutVariants(item, prospectiveStatus)) {
      return internalUpdate(item,
        prospectiveStatus)
        .thenApply(updatedItemResult ->
          updatedItemResult.map(loanAndRelatedRecords::withItem));
    }
    else {
      return skip(loanAndRelatedRecords);
    }
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> onRequestCreation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    try {
      RequestType requestType = RequestType.from(requestAndRelatedRecords.request);

      RequestQueue requestQueue = requestAndRelatedRecords.requestQueue;

      String newStatus = requestQueue.hasOutstandingRequests()
        ? RequestType.from(requestQueue.getHighestPriorityRequest()).toItemStatus()
        : requestType.toItemStatus();

      if (isNotSameStatus(requestAndRelatedRecords.inventoryRecords.item, newStatus)) {
        return internalUpdate(requestAndRelatedRecords.inventoryRecords.item, newStatus)
          .thenApply(updatedItemResult ->
            updatedItemResult.map(requestAndRelatedRecords::withItem));
      } else {
        return skip(requestAndRelatedRecords);
      }
    }
    catch (Exception ex) {
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

  private static boolean isNotSameStatusIgnoringCheckOutVariants(
    JsonObject item,
    String prospectiveStatus) {

    String currentStatus = ItemStatus.getStatus(item);

    // More specific status is ok to retain (will likely be different in each context)
    if(StringUtils.equals(prospectiveStatus, ItemStatus.CHECKED_OUT)) {
      if (ItemStatus.isCheckedOut(currentStatus)) {
        return false;
      } else {
        return isNotSameStatus(currentStatus, prospectiveStatus);
      }
    }
    else {
      return isNotSameStatus(currentStatus, prospectiveStatus);
    }
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

  private String itemStatusFrom(JsonObject loan) {
    switch(loan.getJsonObject("status").getString("name")) {
      case "Open":
        return CHECKED_OUT;

      case "Closed":
        return AVAILABLE;

      default:
        return "";
    }
  }

  private <T> CompletableFuture<HttpResult<T>> skip(T previousResult) {
    return CompletableFuture.completedFuture(HttpResult.success(previousResult));
  }
}
