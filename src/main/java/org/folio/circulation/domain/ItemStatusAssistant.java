package org.folio.circulation.domain;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;

import java.util.function.Consumer;

public class ItemStatusAssistant {
  public static void updateItemWhenLoanChanges(
    String itemId,
    String newItemStatus,
    CollectionResourceClient itemsStorageClient,
    HttpServerResponse responseToClient,
    Consumer<JsonObject> onSuccess) {

    itemsStorageClient.get(itemId, getItemResponse -> {
      if(getItemResponse.getStatusCode() == 200) {
        JsonObject item = getItemResponse.getJson();

          if(itemStatusAlreadyMatches(item, newItemStatus)) {
            onSuccess.accept(item);
          }
          else {
            item.put("status", new JsonObject().put("name", newItemStatus));

            itemsStorageClient.put(itemId,
              item, putItemResponse -> {
                if(putItemResponse.getStatusCode() == 204) {
                  onSuccess.accept(item);
                }
                else {
                  ForwardResponse.forward(responseToClient, putItemResponse);
                }
              });
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

  private static boolean itemStatusAlreadyMatches(JsonObject item, String newItemStatus) {
    return item.getJsonObject("status").getString("name") == newItemStatus;
  }
}
