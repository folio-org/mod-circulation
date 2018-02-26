package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.resources.LoanAndRelatedRecords;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.ItemStatus.*;

public class ItemStatusAssistant {
  private ItemStatusAssistant() { }

  public static CompletableFuture<HttpResult<JsonObject>> updateItemStatus(
    JsonObject item,
    String prospectiveNewStatus,
    CollectionResourceClient itemsStorageClient) {

    CompletableFuture<HttpResult<JsonObject>> itemUpdated = new CompletableFuture<>();

    if (statusNeedsChanging(item, prospectiveNewStatus)) {
      item.put("status", new JsonObject().put("name", prospectiveNewStatus));

      itemsStorageClient.put(item.getString("id"),
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

  public static CompletableFuture<HttpResult<LoanAndRelatedRecords>> updateItemStatus(
    LoanAndRelatedRecords relatedRecords,
    String prospectiveNewStatus,
    CollectionResourceClient itemsStorageClient) {

    return updateItemStatus(relatedRecords.inventoryRecords.getItem(),
      prospectiveNewStatus, itemsStorageClient)
      .thenApply(updatedItemResult -> updatedItemResult.map(relatedRecords::withItem));
  }

  private static boolean statusNeedsChanging(JsonObject item, String prospectiveNewStatus) {
    String currentStatus = getStatus(item);

    // More specific status is ok to retain (will likely be different in each context)
    if(StringUtils.equals(prospectiveNewStatus, ItemStatus.CHECKED_OUT)) {
      if (isCheckedOut(currentStatus)) {
        return false;
      } else {
        return !StringUtils.equals(currentStatus, prospectiveNewStatus);
      }
    }
    else {
      return !StringUtils.equals(currentStatus, prospectiveNewStatus);
    }
  }

  public static boolean isCheckedOut(JsonObject item) {
    return isCheckedOut(getStatus(item));
  }

  public static boolean isCheckedOut(String status) {
    return status.equals(CHECKED_OUT)
      || status.equals(CHECKED_OUT_HELD)
      || status.equals(CHECKED_OUT_RECALLED);
  }

  public static String getStatus(JsonObject item) {
    return item.getJsonObject("status").getString("name");
  }
}
