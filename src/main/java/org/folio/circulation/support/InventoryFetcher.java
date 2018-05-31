package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.ItemRelatedRecord;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InventoryFetcher {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient itemsClient;
  private final CollectionResourceClient holdingsClient;
  private final CollectionResourceClient instancesClient;

  public InventoryFetcher(Clients clients) {
    this(clients.itemsStorage(),
      clients.holdingsStorage(),
      clients.instancesStorage());
  }

  private InventoryFetcher(
    CollectionResourceClient itemsClient,
    CollectionResourceClient holdingsClient,
    CollectionResourceClient instancesClient) {

    this.itemsClient = itemsClient;
    this.holdingsClient = holdingsClient;
    this.instancesClient = instancesClient;
  }

  public CompletableFuture<HttpResult<InventoryRecords>> fetch(ItemRelatedRecord record) {
    return fetchItem(record.getItemId())
      .thenComposeAsync(this::fetchHolding)
      .thenComposeAsync(this::fetchInstance);
  }

  public CompletableFuture<HttpResult<InventoryRecords>> fetchByBarcode(String barcode) {
    return fetchItemByBarcode(barcode)
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

  private JsonObject getRecordFromResponse(Response response) {
    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() == 200) {
        return response.getJson();
      } else {
        return null;
      }
    }
    else {
      //TODO: needs more context in log message
      log.warn("Did not receive response to request");
      return null;
    }
  }

  private CompletableFuture<HttpResult<JsonObject>> fetchItem(String itemId) {
    return new SingleRecordFetcher(itemsClient, "item")
      .fetchSingleRecord(itemId);
  }

  private CompletableFuture<HttpResult<JsonObject>> fetchItemByBarcode(String barcode) {
    CompletableFuture<Response> itemRequestCompleted = new CompletableFuture<>();

    log.info("Fetching item with barcode: {}", barcode);

    itemsClient.getMany(
      String.format("barcode==%s", barcode), 1, 0, itemRequestCompleted::complete);

    return itemRequestCompleted
      .thenApply(this::mapMultipleToResult)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  private HttpResult<JsonObject> mapMultipleToResult(Response response) {
    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() == 200) {
        //TODO: Check for multiple total records
        final MultipleRecordsWrapper wrappedItems =
          MultipleRecordsWrapper.fromBody(response.getBody(), "items");

        return HttpResult.success(wrappedItems.getRecords().stream()
          .findFirst()
          .orElse(null));

      } else {
        return HttpResult.success(null);
      }
    }
    else {
      //TODO: Replace with failure result
      log.warn("Did not receive response to request");
      return HttpResult.success(null);
    }
  }

  private CompletableFuture<HttpResult<InventoryRecords>> fetchHolding(
    HttpResult<JsonObject> itemResult) {

    return itemResult.after(item -> {
      if(item == null) {
        log.info("Item was not found, aborting fetching holding or instance");
        return CompletableFuture.completedFuture(
          HttpResult.success(new InventoryRecords(null, null, null, null, item)));
      }
      else {
        final String holdingsRecordId = item.getString("holdingsRecordId");

        log.info("Fetching holding with ID: {}", holdingsRecordId);

        CompletableFuture<Response> holdingRequestCompleted = new CompletableFuture<>();

        holdingsClient.get(holdingsRecordId, holdingRequestCompleted::complete);

        return holdingRequestCompleted
          .thenApply(response -> HttpResult.success(new InventoryRecords(item,
            getRecordFromResponse(response), null, null, item)))
          .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
      }
    });
  }

  private CompletableFuture<HttpResult<InventoryRecords>> fetchInstance(
    HttpResult<InventoryRecords> holdingResult) {

    return holdingResult.after(inventoryRecords -> {
      final JsonObject holding = inventoryRecords.getHolding();

      if(holding == null) {
        log.info("Holding was not found, aborting fetching instance");

        return CompletableFuture.completedFuture(
          HttpResult.success(new InventoryRecords(
            inventoryRecords.getItem(), null, null, null, holding)));
      }
      else {
        final String instanceId = holding.getString("instanceId");

        log.info("Fetching instance with ID: {}", instanceId);

        CompletableFuture<Response> instanceRequestCompleted = new CompletableFuture<>();

        instancesClient.get(instanceId, instanceRequestCompleted::complete);

        return instanceRequestCompleted
          .thenApply(response -> HttpResult.success(new InventoryRecords(
            inventoryRecords.getItem(), inventoryRecords.getHolding(),
            getRecordFromResponse(response), null, holding)))
          .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
      }
    });
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> getInventoryRecords(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return
      fetch(loanAndRelatedRecords.getLoan())
      .thenApply(result -> result.map(loanAndRelatedRecords::withInventoryRecords));
  }
}
