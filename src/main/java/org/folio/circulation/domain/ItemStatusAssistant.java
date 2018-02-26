package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.resources.RelatedRecords;
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

  public static CompletableFuture<HttpResult<RelatedRecords>> updateItemStatus(
    RelatedRecords relatedRecords,
    String prospectiveNewStatus,
    CollectionResourceClient itemsStorageClient) {

    return updateItemStatus(relatedRecords.inventoryRecords.getItem(),
      prospectiveNewStatus, itemsStorageClient)
      .thenApply(updatedItemResult -> updatedItemResult.map(relatedRecords::replaceItem));
  }

  private static boolean statusNeedsChanging(JsonObject item, String prospectiveNewStatus) {
    String currentStatus = item.getJsonObject("status").getString("name");

    // More specific status is ok to retain (will likely be different in each context)
    if(prospectiveNewStatus == ItemStatus.CHECKED_OUT) {
      if (currentStatus.equals(CHECKED_OUT)
        || currentStatus.equals(CHECKED_OUT_HELD)
        || currentStatus.equals(CHECKED_OUT_RECALLED)) {
        return false;
      } else {
        return currentStatus != prospectiveNewStatus;
      }
    }
    else {
      return currentStatus != prospectiveNewStatus;
    }
  }
}
