package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.ItemRelatedRecord;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LocationRepository;
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

import static java.util.concurrent.CompletableFuture.completedFuture;

public class InventoryFetcher {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient itemsClient;
  private final CollectionResourceClient holdingsClient;
  private final CollectionResourceClient instancesClient;
  private final LocationRepository locationRepository;
  private final boolean fetchLocation;

  public InventoryFetcher(Clients clients, boolean fetchLocation) {
    this(clients.itemsStorage(),
      clients.holdingsStorage(),
      clients.instancesStorage(),
      new LocationRepository(clients),
      fetchLocation);
  }

  private InventoryFetcher(
    CollectionResourceClient itemsClient,
    CollectionResourceClient holdingsClient,
    CollectionResourceClient instancesClient,
    LocationRepository locationRepository,
    boolean fetchLocation) {

    this.itemsClient = itemsClient;
    this.holdingsClient = holdingsClient;
    this.instancesClient = instancesClient;
    this.locationRepository = locationRepository;
    this.fetchLocation = fetchLocation;
  }

  public CompletableFuture<HttpResult<InventoryRecords>> fetchFor(ItemRelatedRecord record) {
    return fetchItem(record.getItemId())
      .thenComposeAsync(this::fetchHolding)
      .thenComposeAsync(this::fetchInstance)
      .thenApply(r -> r.map(InventoryRecordsBuilder::create))
      .thenComposeAsync(this::fetchLocation);
  }

  private CompletableFuture<HttpResult<InventoryRecords>> fetchLocation(HttpResult<InventoryRecords> result) {
    if(fetchLocation) {
      return result.after(locationRepository::getLocation);
    }
    else {
      return completedFuture(result);
    }
  }

  public CompletableFuture<HttpResult<InventoryRecords>> fetchByBarcode(String barcode) {
    return fetchItemByBarcode(barcode)
      .thenComposeAsync(this::fetchHolding)
      .thenComposeAsync(this::fetchInstance)
      .thenApply(r -> r.map(InventoryRecordsBuilder::create))
      .thenComposeAsync(this::fetchLocation);
  }

  public CompletableFuture<MultipleInventoryRecords> fetchFor(
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

          final MultipleInventoryRecords multipleInventoryRecords = MultipleInventoryRecords.from(
            items, holdings, instances);

          final Collection<InventoryRecords> records = multipleInventoryRecords.getRecords();

          if(fetchLocation) {
            locationRepository.getLocations(records).thenAccept(result -> {
              if (result.failed()) {
                onFailure.accept(new Exception(result.cause().toString()));
                return;
              }

              fetchCompleted.complete(new MultipleInventoryRecords(
                records.stream()
                  .map(r -> r.withLocation(result.value().getOrDefault(r.getLocationId(), null)))
                  .collect(Collectors.toList())));
            });
          }
          else {
            fetchCompleted.complete(multipleInventoryRecords);
          }
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

  private CompletableFuture<HttpResult<InventoryRecordsBuilder>> fetchItem(String itemId) {
    return new SingleRecordFetcher(itemsClient, "item")
      .fetchSingleRecord(itemId)
      .thenApply(r -> r.map(InventoryRecordsBuilder::new));
  }

  private CompletableFuture<HttpResult<InventoryRecordsBuilder>> fetchItemByBarcode(String barcode) {
    CompletableFuture<Response> itemRequestCompleted = new CompletableFuture<>();

    log.info("Fetching item with barcode: {}", barcode);

    itemsClient.getMany(
      String.format("barcode==%s", barcode), 1, 0, itemRequestCompleted::complete);

    return itemRequestCompleted
      .thenApply(this::mapMultipleToResult)
      .thenApply(r -> r.map(InventoryRecordsBuilder::new))
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

  private CompletableFuture<HttpResult<InventoryRecordsBuilder>> fetchHolding(
    HttpResult<InventoryRecordsBuilder> result) {

    return result.after(builder -> {
      if(builder == null || builder.item == null) {
        log.info("Item was not found, aborting fetching holding or instance");
        return completedFuture(HttpResult.success(builder));
      }
      else {
        final String holdingsRecordId = builder.getItem().getString("holdingsRecordId");

        log.info("Fetching holding with ID: {}", holdingsRecordId);

        CompletableFuture<Response> holdingRequestCompleted = new CompletableFuture<>();

        holdingsClient.get(holdingsRecordId, holdingRequestCompleted::complete);

        return holdingRequestCompleted
          .thenApply(response -> HttpResult.success(builder.withHoldingsRecord(getRecordFromResponse(response))))
          .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
      }
    });
  }

  private CompletableFuture<HttpResult<InventoryRecordsBuilder>> fetchInstance(
    HttpResult<InventoryRecordsBuilder> holdingResult) {

    return holdingResult.after(builder -> {
      final JsonObject holding = builder.getHoldingsRecord();

      if(holding == null) {
        log.info("Holding was not found, aborting fetching instance");

        return completedFuture(HttpResult.success(builder));
      }
      else {
        final String instanceId = holding.getString("instanceId");

        log.info("Fetching instance with ID: {}", instanceId);

        CompletableFuture<Response> instanceRequestCompleted = new CompletableFuture<>();

        instancesClient.get(instanceId, instanceRequestCompleted::complete);

        return instanceRequestCompleted
          .thenApply(response -> HttpResult.success(builder.withInstance(getRecordFromResponse(response))))
          .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
      }
    });
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> getInventoryRecords(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return
      fetchFor(loanAndRelatedRecords.getLoan())
      .thenApply(result -> result.map(loanAndRelatedRecords::withInventoryRecords));
  }

  private class InventoryRecordsBuilder {
    private final JsonObject item;
    private final JsonObject holdingsRecord;
    private final JsonObject instance;

    InventoryRecordsBuilder(JsonObject item) {
      this(item, null, null);
    }

    private InventoryRecordsBuilder(
      JsonObject item,
      JsonObject holdingsRecord,
      JsonObject instance) {

      this.item = item;
      this.holdingsRecord = holdingsRecord;
      this.instance = instance;
    }

    public InventoryRecords create() {
      return new InventoryRecords(item, holdingsRecord, instance, null, null);
    }

    InventoryRecordsBuilder withHoldingsRecord(JsonObject newHoldingsRecord) {
      return new InventoryRecordsBuilder(
        this.item,
        newHoldingsRecord,
        this.instance
      );
    }

    InventoryRecordsBuilder withInstance(JsonObject newInstance) {
      return new InventoryRecordsBuilder(
        this.item,
        this.holdingsRecord,
        newInstance
      );
    }

    public JsonObject getItem() {
      return item;
    }

    JsonObject getHoldingsRecord() {
      return holdingsRecord;
    }

    public JsonObject getInstance() {
      return instance;
    }
  }
}
