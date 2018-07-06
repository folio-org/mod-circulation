package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.*;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;

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
    return fetchById(record.getItemId());
  }

  private CompletableFuture<HttpResult<Item>> fetchLocation(HttpResult<Item> result) {
    return fetchLocation
      ? result.after(locationRepository::getLocation)
      : completedFuture(result);
  }

  private CompletableFuture<HttpResult<Item>> fetchMaterialType(HttpResult<Item> result) {
    return fetchMaterialType
      ? result.after(materialTypeRepository::getMaterialType)
      : completedFuture(result);
  }

  public CompletableFuture<HttpResult<Item>> fetchByBarcode(String barcode) {
    return fetchItemByBarcode(barcode)
      .thenComposeAsync(this::fetchItemRelatedRecords);
  }

  public CompletableFuture<HttpResult<Item>> fetchById(String itemId) {
    return fetchItem(itemId)
      .thenComposeAsync(this::fetchItemRelatedRecords);
  }

  private CompletableFuture<HttpResult<MultipleInventoryRecords>> fetchLocations(
    HttpResult<MultipleInventoryRecords> result) {

    if(fetchLocation) {
      return result.after(records ->
        locationRepository.getLocations(result.value().getRecords())
          .thenApply(locationResult -> {
            if (locationResult.failed()) {
              return failed(locationResult.cause());
            }

            return succeeded(new MultipleInventoryRecords(
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
              return failed(materialTypeResult.cause());
            }

            return succeeded(new MultipleInventoryRecords(
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
          return failed(new ServerErrorFailure(
            String.format("Instances request (%s) failed %s: %s",
              instancesQuery, instancesResponse.getStatusCode(),
              instancesResponse.getBody())));
        }

        final List<JsonObject> instances = JsonArrayHelper.toList(
          instancesResponse.getJson().getJsonArray("instances"));

        return succeeded(MultipleInventoryRecords.from(
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
          return failed(
            new ServerErrorFailure(String.format("Holdings request (%s) failed %s: %s",
              holdingsQuery, holdingsResponse.getStatusCode(),
              holdingsResponse.getBody())));
        }

        final List<JsonObject> holdings = JsonArrayHelper.toList(
          holdingsResponse.getJson().getJsonArray("holdingsRecords"));

        return succeeded(MultipleInventoryRecords.from(
          records.getItems(), holdings, new ArrayList<>()));
      });
    });
  }

  private CompletableFuture<HttpResult<MultipleInventoryRecords>> fetchItems(
    Collection<String> itemIds) {

    String itemsQuery = CqlHelper.multipleRecordsCqlQuery(itemIds);

    return itemsClient.getMany(itemsQuery, itemIds.size(), 0)
      .thenApply(r -> MultipleRecords.from(r, identity(), "items"))
      .thenApply(r -> r.map(multipleItems ->
        MultipleInventoryRecords.from(multipleItems.getRecords(),
          new ArrayList<>(), new ArrayList<>())));
  }

  private CompletableFuture<HttpResult<Item>> fetchItem(String itemId) {
    return SingleRecordFetcher.jsonOrNull(itemsClient, "item")
      .fetchSingleRecord(itemId)
      .thenApply(r -> r.map(Item::from));
  }

  private CompletableFuture<HttpResult<Item>> fetchItemByBarcode(String barcode) {
    log.info("Fetching item with barcode: {}", barcode);

    return itemsClient.getMany(String.format("barcode==%s", barcode), 1, 0)
      .thenApply(this::mapMultipleToResult)
      .thenApply(r -> r.map(Item::from))
      .exceptionally(e -> failed(new ServerErrorFailure(e)));
  }

  private HttpResult<JsonObject> mapMultipleToResult(Response response) {
    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() == 200) {
        //TODO: Check for multiple total records
        final MultipleRecordsWrapper wrappedItems =
          MultipleRecordsWrapper.fromBody(response.getBody(), "items");

        return succeeded(wrappedItems.getRecords().stream()
          .findFirst()
          .orElse(null));

      } else {
        return succeeded(null);
      }
    }
    else {
      //TODO: Replace with failure result
      log.warn("Did not receive response to request");
      return succeeded(null);
    }
  }

  private CompletableFuture<HttpResult<Item>> fetchHoldingsRecord(
    HttpResult<Item> result) {

    return result.after(item -> {
      if(item == null || item.isNotFound()) {
        log.info("Item was not found, aborting fetching holding or instance");
        return completedFuture(succeeded(item));
      }
      else {
        return SingleRecordFetcher.jsonOrNull(holdingsClient, "holding")
          .fetchSingleRecord(item.getHoldingsRecordId())
          .thenApply(r -> r.map(item::withHoldingsRecord));
      }
    });
  }

  private CompletableFuture<HttpResult<Item>> fetchInstance(HttpResult<Item> result) {
    return result.after(item -> {
      if(item == null || item.isNotFound() || item.getInstanceId() == null) {
        log.info("Holding was not found, aborting fetching instance");
        return completedFuture(succeeded(item));
      }
      else {
        return SingleRecordFetcher.jsonOrNull(instancesClient, "instance")
          .fetchSingleRecord(item.getInstanceId())
          .thenApply(r -> r.map(item::withInstance));
      }
    });
  }

  //TODO: Try to remove includeItemMap without introducing unchecked exception
  public <T extends ItemRelatedRecord> CompletableFuture<HttpResult<MultipleRecords<T>>> fetchItemsFor(
    HttpResult<MultipleRecords<T>> result,
    BiFunction<T, Item, T> includeItemMap) {

    return result.combineAfter(r -> fetchFor(getItemIds(r)),
      (records, items) -> new MultipleRecords<>(
        matchItemToRecord(records, items, includeItemMap),
        records.getTotalRecords()));
  }

  private CompletableFuture<HttpResult<MultipleInventoryRecords>> fetchFor(
    Collection<String> itemIds) {

    return fetchItems(itemIds)
      .thenComposeAsync(this::fetchHoldingRecords)
      .thenComposeAsync(this::fetchInstances)
      .thenComposeAsync(this::fetchLocations)
      .thenComposeAsync(this::fetchMaterialTypes);
  }

  private <T extends ItemRelatedRecord> List<String> getItemIds(MultipleRecords<T> records) {
    return records.getRecords().stream()
      .map(ItemRelatedRecord::getItemId)
      .collect(Collectors.toList());
  }

  private <T extends ItemRelatedRecord> Collection<T> matchItemToRecord(
    MultipleRecords<T> records,
    MultipleInventoryRecords items,
    BiFunction<T, Item, T> includeItemMap) {

    return records.getRecords().stream()
      .map(r -> includeItemMap.apply(r, items.findRecordByItemId(r.getItemId())))
      .collect(Collectors.toList());
  }

  private CompletableFuture<HttpResult<Item>> fetchItemRelatedRecords(
    HttpResult<Item> item) {

    return fetchHoldingsRecord(item)
      .thenComposeAsync(this::fetchInstance)
      .thenComposeAsync(this::fetchLocation)
      .thenComposeAsync(this::fetchMaterialType);
  }
}
