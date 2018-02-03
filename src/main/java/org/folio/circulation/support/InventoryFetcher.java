package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class InventoryFetcher {
  private final CollectionResourceClient itemsClient;
  private final CollectionResourceClient holdingsClient;
  private final CollectionResourceClient instancesClient;

  public InventoryFetcher(
    CollectionResourceClient itemsClient,
    CollectionResourceClient holdingsClient,
    CollectionResourceClient instancesClient) {

    this.itemsClient = itemsClient;
    this.holdingsClient = holdingsClient;
    this.instancesClient = instancesClient;
  }

  public CompletableFuture<InventoryRecords> fetch(
    String itemId,
    Consumer<Exception> onFailure) {

    CompletableFuture<Response> itemRequestCompleted = new CompletableFuture<>();
    CompletableFuture<Response> holdingRequestCompleted = new CompletableFuture<>();
    CompletableFuture<Response> instanceRequestCompleted = new CompletableFuture<>();

    itemsClient.get(itemId, itemRequestCompleted::complete);

    itemRequestCompleted.thenAccept(itemResponse -> {
      try {
        if(itemResponse != null && itemResponse.getStatusCode() == 200) {
          JsonObject item = itemResponse.getJson();

          holdingsClient.get(item.getString("holdingsRecordId"),
            holdingRequestCompleted::complete);
        }
        else {
          holdingRequestCompleted.complete(null);
          instanceRequestCompleted.complete(null);
        }
      }
      catch(Exception e) {
        onFailure.accept(e);
      }
    });

    holdingRequestCompleted.thenAccept(holdingResponse -> {
      try {
        if(holdingResponse != null && holdingResponse.getStatusCode() == 200) {
          JsonObject holding = holdingResponse.getJson();

          instancesClient.get(holding.getString("instanceId"),
            instanceRequestCompleted::complete);
        }
        else {
          instanceRequestCompleted.complete(null);
        }
      }
      catch(Exception e) {
        onFailure.accept(e);
      }
    });

    CompletableFuture<Void> allCompleted = CompletableFuture.allOf(
      itemRequestCompleted,
      holdingRequestCompleted,
      instanceRequestCompleted);

    CompletableFuture<InventoryRecords> recordsCompleted = new CompletableFuture<>();

    allCompleted.thenAccept(v -> {
      JsonObject item = getRecordFromResponse(itemRequestCompleted.join());
      JsonObject holding = getRecordFromResponse(holdingRequestCompleted.join());
      JsonObject instance = getRecordFromResponse(instanceRequestCompleted.join());

      recordsCompleted.complete(new InventoryRecords(item, holding, instance));
    });

    return recordsCompleted;
  }

  private JsonObject getRecordFromResponse(Response itemResponse) {
    return itemResponse != null && itemResponse.getStatusCode() == 200
      ? itemResponse.getJson()
      : null;
  }
}
