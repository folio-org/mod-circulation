package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemRelatedRecord;
import org.folio.circulation.domain.LocationRepository;
import org.folio.circulation.domain.MaterialTypeRepository;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class ItemRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient itemsClient;
  private final CollectionResourceClient holdingsClient;
  private final CollectionResourceClient instancesClient;
  private final LocationRepository locationRepository;
  private final MaterialTypeRepository materialTypeRepository;
  private final boolean fetchLocation;
  private final boolean fetchMaterialType;

  public ItemRepository(
    Clients clients,
    boolean fetchLocation,
    boolean fetchMaterialType) {

    this(clients.itemsStorage(),
      clients.holdingsStorage(),
      clients.instancesStorage(),
      new LocationRepository(clients),
      new MaterialTypeRepository(clients),
      fetchLocation, fetchMaterialType);
  }

  private ItemRepository(
    CollectionResourceClient itemsClient,
    CollectionResourceClient holdingsClient,
    CollectionResourceClient instancesClient,
    LocationRepository locationRepository,
    MaterialTypeRepository materialTypeRepository,
    boolean fetchLocation,
    boolean fetchMaterialType) {

    this.itemsClient = itemsClient;
    this.holdingsClient = holdingsClient;
    this.instancesClient = instancesClient;
    this.locationRepository = locationRepository;
    this.materialTypeRepository = materialTypeRepository;
    this.fetchLocation = fetchLocation;
    this.fetchMaterialType = fetchMaterialType;
  }

  public CompletableFuture<HttpResult<Item>> fetchFor(ItemRelatedRecord record) {
    return fetchItem(record.getItemId())
      .thenComposeAsync(this::fetchHolding)
      .thenComposeAsync(this::fetchInstance)
      .thenApply(r -> r.map(InventoryRecordsBuilder::create))
      .thenComposeAsync(this::fetchLocation)
      .thenComposeAsync(this::fetchMaterialType);
  }

  private CompletableFuture<HttpResult<Item>> fetchLocation(
    HttpResult<Item> result) {

    if(fetchLocation) {
      return result.after(locationRepository::getLocation);
    }
    else {
      return completedFuture(result);
    }
  }

  private CompletableFuture<HttpResult<Item>> fetchMaterialType(
    HttpResult<Item> result) {

    if(fetchMaterialType) {
      return result.after(materialTypeRepository::getMaterialType);
    }
    else {
      return completedFuture(result);
    }
  }

  public CompletableFuture<HttpResult<Item>> fetchByBarcode(String barcode) {
    return fetchItemByBarcode(barcode)
      .thenComposeAsync(this::fetchHolding)
      .thenComposeAsync(this::fetchInstance)
      .thenApply(r -> r.map(InventoryRecordsBuilder::create))
      .thenComposeAsync(this::fetchLocation)
      .thenComposeAsync(this::fetchMaterialType);
  }

  public CompletableFuture<HttpResult<Item>> fetchById(String itemId) {
    return fetchItem(itemId)
      .thenComposeAsync(this::fetchHolding)
      .thenComposeAsync(this::fetchInstance)
      .thenApply(r -> r.map(InventoryRecordsBuilder::create))
      .thenComposeAsync(this::fetchLocation)
      .thenComposeAsync(this::fetchMaterialType);
  }

  public CompletableFuture<HttpResult<MultipleInventoryRecords>> fetchFor(
    Collection<String> itemIds) {

    return fetchItems(itemIds)
      .thenComposeAsync(this::fetchHoldingRecords)
      .thenComposeAsync(this::fetchInstances)
      .thenComposeAsync(this::fetchLocations)
      .thenComposeAsync(this::fetchMaterialTypes);
  }

  private CompletableFuture<HttpResult<MultipleInventoryRecords>> fetchLocations(
    HttpResult<MultipleInventoryRecords> result) {

    if(fetchLocation) {
      return result.after(records ->
        locationRepository.getLocations(result.value().getRecords())
          .thenApply(locationResult -> {
            if (locationResult.failed()) {
              return HttpResult.failed(locationResult.cause());
            }

            return HttpResult.succeeded(new MultipleInventoryRecords(
              records.getItems(),
              records.getHoldings(),
              records.getInstances(),
              records.getRecords().stream()
                .map(r -> r.withLocation(locationResult.value()
                  .getOrDefault(r.getLocationId(), null)))
                .collect(Collectors.toList())));
        }));
    }
    else {
      return completedFuture(result);
    }
  }

  private CompletableFuture<HttpResult<MultipleInventoryRecords>> fetchMaterialTypes(
    HttpResult<MultipleInventoryRecords> result) {

    if(fetchMaterialType) {
      return result.after(records ->
        materialTypeRepository.getMaterialTypes(records.getRecords())
          .thenApply(materialTypeResult -> {
            if (materialTypeResult.failed()) {
              return HttpResult.failed(materialTypeResult.cause());
            }

            return HttpResult.succeeded(new MultipleInventoryRecords(
              records.getItems(),
              records.getHoldings(),
              records.getInstances(),
              records.getRecords().stream()
                .map(r -> r.withMaterialType(materialTypeResult.value()
                  .getOrDefault(r.getMaterialTypeId(), null)))
                .collect(Collectors.toList())));
        }));
    }
    else {
      return completedFuture(result);
    }
  }

  private CompletableFuture<HttpResult<MultipleInventoryRecords>> fetchInstances(
    HttpResult<MultipleInventoryRecords> result) {

    return result.after(records -> {
      List<String> instanceIds = records.getRecords().stream()
        .map(Item::getInstanceId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

      String instancesQuery = CqlHelper.multipleRecordsCqlQuery(instanceIds);

      return instancesClient.getMany(instancesQuery, instanceIds.size(), 0).thenApply(instancesResponse -> {
        if (instancesResponse.getStatusCode() != 200) {
          return HttpResult.failed(new ServerErrorFailure(
            String.format("Instances request (%s) failed %s: %s",
              instancesQuery, instancesResponse.getStatusCode(),
              instancesResponse.getBody())));
        }

        final List<JsonObject> instances = JsonArrayHelper.toList(
          instancesResponse.getJson().getJsonArray("instances"));

        return HttpResult.succeeded(MultipleInventoryRecords.from(
          records.getItems(), records.getHoldings(), instances));
      });
    });
  }

  private CompletableFuture<HttpResult<MultipleInventoryRecords>> fetchHoldingRecords(
    HttpResult<MultipleInventoryRecords> result) {

    return result.after(records -> {
      List<String> holdingsIds = records.getRecords().stream()
        .map(Item::getHoldingsRecordId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

      String holdingsQuery = CqlHelper.multipleRecordsCqlQuery(holdingsIds);

      return holdingsClient.getMany(holdingsQuery, holdingsIds.size(), 0).thenApply(holdingsResponse -> {
        if(holdingsResponse.getStatusCode() != 200) {
          return HttpResult.failed(
            new ServerErrorFailure(String.format("Holdings request (%s) failed %s: %s",
              holdingsQuery, holdingsResponse.getStatusCode(),
              holdingsResponse.getBody())));
        }

        final List<JsonObject> holdings = JsonArrayHelper.toList(
          holdingsResponse.getJson().getJsonArray("holdingsRecords"));

        return HttpResult.succeeded(MultipleInventoryRecords.from(
          records.getItems(), holdings, new ArrayList<>()));
      });
    });
  }

  private CompletableFuture<HttpResult<MultipleInventoryRecords>> fetchItems(
    Collection<String> itemIds) {

    String itemsQuery = CqlHelper.multipleRecordsCqlQuery(itemIds);

    return itemsClient.getMany(itemsQuery, itemIds.size(), 0).thenApply(response -> {
      if(response.getStatusCode() != 200) {
        return HttpResult.failed(
          new ServerErrorFailure(String.format("Items request (%s) failed %s: %s",
            itemsQuery, response.getStatusCode(), response.getBody())));
      }

      final List<JsonObject> items = JsonArrayHelper.toList(
        response.getJson().getJsonArray("items"));

      return HttpResult.succeeded(MultipleInventoryRecords.from(items,
        new ArrayList<>(), new ArrayList<>()));
    });
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
    log.info("Fetching item with barcode: {}", barcode);

    return itemsClient.getMany(String.format("barcode==%s", barcode), 1, 0)
      .thenApply(this::mapMultipleToResult)
      .thenApply(r -> r.map(InventoryRecordsBuilder::new))
      .exceptionally(e -> HttpResult.failed(new ServerErrorFailure(e)));
  }

  private HttpResult<JsonObject> mapMultipleToResult(Response response) {
    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() == 200) {
        //TODO: Check for multiple total records
        final MultipleRecordsWrapper wrappedItems =
          MultipleRecordsWrapper.fromBody(response.getBody(), "items");

        return HttpResult.succeeded(wrappedItems.getRecords().stream()
          .findFirst()
          .orElse(null));

      } else {
        return HttpResult.succeeded(null);
      }
    }
    else {
      //TODO: Replace with failure result
      log.warn("Did not receive response to request");
      return HttpResult.succeeded(null);
    }
  }

  private CompletableFuture<HttpResult<InventoryRecordsBuilder>> fetchHolding(
    HttpResult<InventoryRecordsBuilder> result) {

    return result.after(builder -> {
      if(builder == null || builder.item == null) {
        log.info("Item was not found, aborting fetching holding or instance");
        return completedFuture(HttpResult.succeeded(builder));
      }
      else {
        final String holdingsRecordId = builder.getItem().getString("holdingsRecordId");

        log.info("Fetching holding with ID: {}", holdingsRecordId);

        return holdingsClient.get(holdingsRecordId)
          .thenApply(response -> HttpResult.succeeded(
            builder.withHoldingsRecord(getRecordFromResponse(response))))
          .exceptionally(e -> HttpResult.failed(new ServerErrorFailure(e)));
      }
    });
  }

  private CompletableFuture<HttpResult<InventoryRecordsBuilder>> fetchInstance(
    HttpResult<InventoryRecordsBuilder> holdingResult) {

    return holdingResult.after(builder -> {
      final JsonObject holding = builder.getHoldingsRecord();

      if(holding == null) {
        log.info("Holding was not found, aborting fetching instance");

        return completedFuture(HttpResult.succeeded(builder));
      }
      else {
        final String instanceId = holding.getString("instanceId");

        log.info("Fetching instance with ID: {}", instanceId);

        return instancesClient.get(instanceId)
          .thenApply(response -> HttpResult.succeeded(
            builder.withInstance(getRecordFromResponse(response))))
          .exceptionally(e -> HttpResult.failed(new ServerErrorFailure(e)));
      }
    });
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

    public Item create() {
      return new Item(item, holdingsRecord, instance, null, null);
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
