package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InventoryFetcher {
  private final CollectionResourceClient itemsClient;
  private final CollectionResourceClient holdingsClient;
  private final CollectionResourceClient instancesClient;

  public static InventoryFetcher create(Clients clients) {
    return new InventoryFetcher(clients.itemsStorage(),
      clients.holdingsStorage(), clients.instancesStorage());
  }

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

  public CompletableFuture<MultipleInventoryRecords> fetch(
    Collection<String> itemIds,
    Consumer<Exception> onFailure) {

    CompletableFuture<MultipleInventoryRecords> fetchCompleted = new CompletableFuture<>();

    CompletableFuture<Response> itemsFetched = new CompletableFuture<>();

    String itemsQuery = CqlHelper.multipleRecordsCqlQuery(itemIds);

    itemsClient.getMany(itemsQuery, itemIds.size(), 0,
      itemsFetched::complete);

    itemsFetched.thenAccept(itemsResponse -> {
      if(itemsResponse.getStatusCode() != 200) {
        //Improve this to not need an exception
        onFailure.accept(new Exception(
          String.format("Items request (%s) failed %s: %s",
            itemsQuery, itemsResponse.getStatusCode(), itemsResponse.getBody())));
        return;
      }

      final List<JsonObject> items = JsonArrayHelper.toList(
        itemsResponse.getJson().getJsonArray("items"));

      List<String> holdingsIds = items.stream()
        .map(item -> item.getString("holdingsRecordId"))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

      CompletableFuture<Response> holdingsFetched =
        new CompletableFuture<>();

      String holdingsQuery = CqlHelper.multipleRecordsCqlQuery(holdingsIds);

      holdingsClient.getMany(holdingsQuery, holdingsIds.size(), 0,
        holdingsFetched::complete);

      holdingsFetched.thenAccept(holdingsResponse -> {
        if(holdingsResponse.getStatusCode() != 200) {
          //Improve this to not need an exception
          onFailure.accept(new Exception(String.format("Holdings request (%s) failed %s: %s",
            holdingsQuery, holdingsResponse.getStatusCode(),
            holdingsResponse.getBody())));
          return;
        }

        final List<JsonObject> holdings = JsonArrayHelper.toList(
          holdingsResponse.getJson().getJsonArray("holdingsRecords"));

        List<String> instanceIds = holdings.stream()
          .map(holding -> holding.getString("instanceId"))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

        CompletableFuture<Response> instancesFetched = new CompletableFuture<>();

        String instancesQuery = CqlHelper.multipleRecordsCqlQuery(instanceIds);

        instancesClient.getMany(instancesQuery, instanceIds.size(), 0,
          instancesFetched::complete);

        instancesFetched.thenAccept(instancesResponse -> {
          if (instancesResponse.getStatusCode() != 200) {
            //Improve this to not need an exception
            onFailure.accept(new Exception(String.format("Instances request (%s) failed %s: %s",
              instancesQuery, instancesResponse.getStatusCode(),
              instancesResponse.getBody())));
            return;
          }

          final List<JsonObject> instances = JsonArrayHelper.toList(
            instancesResponse.getJson().getJsonArray("instances"));

          fetchCompleted.complete(new MultipleInventoryRecords(items, holdings, instances));
        });
      });
    });

    return fetchCompleted;
  }

  private JsonObject getRecordFromResponse(Response itemResponse) {
    return itemResponse != null && itemResponse.getStatusCode() == 200
      ? itemResponse.getJson()
      : null;
  }
}
