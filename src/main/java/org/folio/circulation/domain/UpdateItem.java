package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.resources.LoanAndRelatedRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;

import java.util.concurrent.CompletableFuture;

public class UpdateItem {
  private final CollectionResourceClient itemsStorageClient;

  public UpdateItem(Clients clients) {
    itemsStorageClient = clients.itemsStorage();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> whilstCheckedOut(
    LoanAndRelatedRecords relatedRecords,
    String prospectiveNewStatus) {

    return updateItemStatus(relatedRecords.inventoryRecords.getItem(),
      prospectiveNewStatus)
      .thenApply(updatedItemResult -> updatedItemResult.map(relatedRecords::withItem));
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onLoanUpdate(
    LoanAndRelatedRecords relatedRecords,
    String prospectiveNewStatus) {

    return updateItemStatus(relatedRecords.inventoryRecords.getItem(),
      prospectiveNewStatus)
      .thenApply(updatedItemResult -> updatedItemResult.map(relatedRecords::withItem));
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> updateItemStatus(
    LoanAndRelatedRecords relatedRecords,
    String prospectiveNewStatus) {

    return updateItemStatus(relatedRecords.inventoryRecords.getItem(),
      prospectiveNewStatus)
      .thenApply(updatedItemResult -> updatedItemResult.map(relatedRecords::withItem));
  }

  public CompletableFuture<HttpResult<JsonObject>> updateItemStatus(
    JsonObject item,
    String prospectiveNewStatus) {

    CompletableFuture<HttpResult<JsonObject>> itemUpdated = new CompletableFuture<>();

    if (statusNeedsChanging(item, prospectiveNewStatus)) {
      item.put("status", new JsonObject().put("name", prospectiveNewStatus));

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
    } else {
      itemUpdated.complete(HttpResult.success(item));
    }

    return itemUpdated;
  }

  private static boolean statusNeedsChanging(
    JsonObject item,
    String prospectiveNewStatus) {

    String currentStatus = ItemStatus.getStatus(item);

    // More specific status is ok to retain (will likely be different in each context)
    if(StringUtils.equals(prospectiveNewStatus, ItemStatus.CHECKED_OUT)) {
      if (ItemStatus.isCheckedOut(currentStatus)) {
        return false;
      } else {
        return !StringUtils.equals(currentStatus, prospectiveNewStatus);
      }
    }
    else {
      return !StringUtils.equals(currentStatus, prospectiveNewStatus);
    }
  }
}
