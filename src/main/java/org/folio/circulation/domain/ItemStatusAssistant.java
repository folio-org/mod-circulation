package org.folio.circulation.domain;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;

import java.util.function.Consumer;

import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT_HELD;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT_RECALLED;

public class ItemStatusAssistant {
  public static void updateItemStatus(
    String itemId,
    String prospectiveNewStatus,
    CollectionResourceClient itemsStorageClient,
    HttpServerResponse responseToClient,
    Consumer<JsonObject> onSuccess) {

    itemsStorageClient.get(itemId, getItemResponse -> {
      if(getItemResponse.getStatusCode() == 200) {
        JsonObject item = getItemResponse.getJson();
        if (statusNeedsChanging(item, prospectiveNewStatus)) {
          item.put("status", new JsonObject().put("name", prospectiveNewStatus));

          itemsStorageClient.put(itemId,
            item, putItemResponse -> {
              if(putItemResponse.getStatusCode() == 204) {
                onSuccess.accept(item);
              }
              else {
                ForwardResponse.forward(responseToClient, putItemResponse);
              }
            });
        } else {
          onSuccess.accept(item);
        }
      }
      else if(getItemResponse.getStatusCode() == 404) {
        ServerErrorResponse.internalError(responseToClient,
          "Failed to handle updating an item which does not exist");
      }
      else {
        ForwardResponse.forward(responseToClient, getItemResponse);
      }
    });
  }

  public static boolean statusNeedsChanging(JsonObject item, String prospectiveNewStatus) {
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
