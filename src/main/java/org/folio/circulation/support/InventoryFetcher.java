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

  public InventoryFetcher(Clients clients) {
    this(clients.itemsStorage(),
      clients.holdingsStorage(), clients.instancesStorage());
  }

  private InventoryFetcher(
    CollectionResourceClient itemsClient,
    CollectionResourceClient holdingsClient,
    CollectionResourceClient instancesClient) {

    this.itemsClient = itemsClient;
    this.holdingsClient = holdingsClient;
    this.instancesClient = instancesClient;
  }

  public CompletableFuture<HttpResult<InventoryRecords>> fetch(JsonObject loan) {
    return fetch(loan.getString("itemId"));
  }

  public CompletableFuture<HttpResult<InventoryRecords>> fetch(String itemId) {
    return
      fetchItem(itemId)
        .thenComposeAsync(this::fetchHolding)
        .thenComposeAsync(this::fetchInstance);
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

  private CompletableFuture<HttpResult<JsonObject>> fetchItem(String itemId) {
    CompletableFuture<Response> itemRequestCompleted = new CompletableFuture<>();

    itemsClient.get(itemId, itemRequestCompleted::complete);

    return itemRequestCompleted
      .thenApply(this::mapToResult)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  private HttpResult<JsonObject> mapToResult(Response response) {
    if (response != null && response.getStatusCode() == 200) {
      return HttpResult.success(response.getJson());
    } else {
      //TODO: Handle different failure cases
      return HttpResult.success(null);
    }
  }

  private CompletableFuture<HttpResult<InventoryRecords>> fetchHolding(
    HttpResult<JsonObject> itemResult) {

    return itemResult.after(item -> {
      if(item == null) {
        return CompletableFuture.completedFuture(
          HttpResult.success(new InventoryRecords(null, null, null)));
      }
      else {
        CompletableFuture<Response> holdingRequestCompleted = new CompletableFuture<>();

        holdingsClient.get(item.getString("holdingsRecordId"),
          holdingRequestCompleted::complete);

        return holdingRequestCompleted
          .thenApply(response -> HttpResult.success(new InventoryRecords(item,
            getRecordFromResponse(response), null)))
          .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
      }
    });
  }

  private CompletableFuture<HttpResult<InventoryRecords>> fetchInstance(
    HttpResult<InventoryRecords> holdingResult) {

    return holdingResult.after(inventoryRecords -> {
      final JsonObject holding = inventoryRecords.getHolding();

      if(holding == null) {
        return CompletableFuture.completedFuture(
          HttpResult.success(new InventoryRecords(
            inventoryRecords.getItem(), null, null)));
      }
      else {
        CompletableFuture<Response> instanceRequestCompleted = new CompletableFuture<>();

        instancesClient.get(holding.getString("instanceId"),
          instanceRequestCompleted::complete);

        return instanceRequestCompleted
          .thenApply(response -> HttpResult.success(new InventoryRecords(
            inventoryRecords.getItem(), inventoryRecords.getHolding(),
            getRecordFromResponse(response))))
          .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
      }
    });
  }
}
