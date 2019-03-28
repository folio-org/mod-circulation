package org.folio.circulation.support;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemRelatedRecord;
import org.folio.circulation.domain.LocationRepository;
import org.folio.circulation.domain.MaterialTypeRepository;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class ItemRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient itemsClient;
  private final CollectionResourceClient holdingsClient;
  private final CollectionResourceClient instancesClient;
  private final LocationRepository locationRepository;
  private final MaterialTypeRepository materialTypeRepository;
  private final ServicePointRepository servicePointRepository;
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
      new ServicePointRepository(clients),
      fetchLocation, fetchMaterialType);
  }

  private ItemRepository(
    CollectionResourceClient itemsClient,
    CollectionResourceClient holdingsClient,
    CollectionResourceClient instancesClient,
    LocationRepository locationRepository,
    MaterialTypeRepository materialTypeRepository,
    ServicePointRepository servicePointRepository,
    boolean fetchLocation,
    boolean fetchMaterialType) {

    this.itemsClient = itemsClient;
    this.holdingsClient = holdingsClient;
    this.instancesClient = instancesClient;
    this.locationRepository = locationRepository;
    this.materialTypeRepository = materialTypeRepository;
    this.servicePointRepository = servicePointRepository;
    this.fetchLocation = fetchLocation;
    this.fetchMaterialType = fetchMaterialType;
  }

  public CompletableFuture<Result<Item>> fetchFor(ItemRelatedRecord record) {
    return fetchById(record.getItemId());
  }

  private CompletableFuture<Result<Item>> fetchLocation(Result<Item> result) {
    return fetchLocation
      ? result.combineAfter(locationRepository::getLocation, Item::withLocation)
          .thenComposeAsync(itemResult ->
          itemResult.combineAfter(item ->
              servicePointRepository.getServicePointById(
                item.getPrimaryServicePointId()), Item::withPrimaryServicePoint))
      : completedFuture(result);
  }

  private CompletableFuture<Result<Item>> fetchMaterialType(Result<Item> result) {
    return fetchMaterialType
      ? result.combineAfter(materialTypeRepository::getFor, Item::withMaterialType)
      : completedFuture(result);
  }

  public CompletableFuture<Result<Item>> fetchByBarcode(String barcode) {
    return fetchItemByBarcode(barcode)
      .thenComposeAsync(this::fetchItemRelatedRecords);
  }

  public CompletableFuture<Result<Item>> fetchById(String itemId) {
    return fetchItem(itemId)
      .thenComposeAsync(this::fetchItemRelatedRecords);
  }

  private CompletableFuture<Result<Collection<Item>>> fetchLocations(
    Result<Collection<Item>> result) {

    if(fetchLocation) {
      return result.after(items ->
        locationRepository.getLocations(items)
          .thenApply(r -> r.map(locations -> items.stream()
              .map(item -> item.withLocation(locations
                .getOrDefault(item.getLocationId(), null)))
              .collect(Collectors.toList()))));
    }
    else {
      return completedFuture(result);
    }
  }

  private CompletableFuture<Result<Collection<Item>>> fetchMaterialTypes(
    Result<Collection<Item>> result) {

    if(fetchMaterialType) {
      return result.after(items ->
        materialTypeRepository.getMaterialTypes(items)
          .thenApply(r -> r.map(materialTypes -> items.stream()
              .map(item -> item.withMaterialType(materialTypes
                .getOrDefault(item.getMaterialTypeId(), null)))
              .collect(Collectors.toList()))));
    }
    else {
      return completedFuture(result);
    }
  }

  private CompletableFuture<Result<Collection<Item>>> fetchInstances(
    Result<Collection<Item>> result) {

    return result.after(items -> {
      List<String> instanceIds = items.stream()
        .map(Item::getInstanceId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

      String instancesQuery = CqlHelper.multipleRecordsCqlQuery(instanceIds);

      return instancesClient.getMany(instancesQuery, instanceIds.size(), 0)
        .thenApply(instancesResponse ->
          MultipleRecords.from(instancesResponse, identity(), "instances"))
        .thenApply(r -> r.map(instances -> items.stream()
          .map(item -> item.withInstance(
            findById(item.getInstanceId(), instances.getRecords()).orElse(null)))
          .collect(Collectors.toList())));
    });
  }

  private CompletableFuture<Result<Collection<Item>>> fetchHoldingRecords(
    Result<Collection<Item>> result) {

    return result.after(items -> {
      List<String> holdingsIds = items.stream()
        .map(Item::getHoldingsRecordId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

      String holdingsQuery = CqlHelper.multipleRecordsCqlQuery(holdingsIds);

      return holdingsClient.getMany(holdingsQuery, holdingsIds.size(), 0)
        .thenApply(holdingsResponse ->
          MultipleRecords.from(holdingsResponse, identity(), "holdingsRecords"))
        .thenApply(r -> r.map(holdings -> items.stream()
          .map(item -> item.withHoldingsRecord(
            findById(item.getHoldingsRecordId(), holdings.getRecords()).orElse(null)))
          .collect(Collectors.toList())));
    });
  }

  private static Optional<JsonObject> findById(
    String id,
    Collection<JsonObject> collection) {

    return collection.stream()
      .filter(item -> item.getString("id").equals(id))
      .findFirst();
  }

  private CompletableFuture<Result<Collection<Item>>> fetchItems(
    Collection<String> itemIds) {

    String itemsQuery = CqlHelper.multipleRecordsCqlQuery(itemIds);

    return itemsClient.getMany(itemsQuery, itemIds.size(), 0)
      .thenApply(r -> MultipleRecords.from(r, Item::from, "items"))
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  private CompletableFuture<Result<Item>> fetchItem(String itemId) {
    return SingleRecordFetcher.jsonOrNull(itemsClient, "item")
      .fetch(itemId)
      .thenApply(r -> r.map(Item::from));
  }

  private CompletableFuture<Result<Item>> fetchItemByBarcode(String barcode) {
    log.info("Fetching item with barcode: {}", barcode);

    return itemsClient.getMany(String.format("barcode==%s", barcode), 1, 0)
      .thenApply(this::mapMultipleToResult)
      .thenApply(r -> r.map(Item::from))
      .exceptionally(e -> failed(new ServerErrorFailure(e)));
  }

  private Result<JsonObject> mapMultipleToResult(Response response) {
    return MultipleRecords.from(response, identity(), "items")
      .map(items -> items.getRecords().stream().findFirst().orElse(null));
  }

  private CompletableFuture<Result<Item>> fetchHoldingsRecord(
    Result<Item> result) {

    return result.after(item -> {
      if(item == null || item.isNotFound()) {
        log.info("Item was not found, aborting fetching holding or instance");
        return completedFuture(succeeded(item));
      }
      else {
        return SingleRecordFetcher.jsonOrNull(holdingsClient, "holding")
          .fetch(item.getHoldingsRecordId())
          .thenApply(r -> r.map(item::withHoldingsRecord));
      }
    });
  }

  private CompletableFuture<Result<Item>> fetchInstance(Result<Item> result) {
    return result.after(item -> {
      if(item == null || item.isNotFound() || item.getInstanceId() == null) {
        log.info("Holding was not found, aborting fetching instance");
        return completedFuture(succeeded(item));
      }
      else {
        return SingleRecordFetcher.jsonOrNull(instancesClient, "instance")
          .fetch(item.getInstanceId())
          .thenApply(r -> r.map(item::withInstance));
      }
    });
  }

  //TODO: Try to remove includeItemMap without introducing unchecked exception
  public <T extends ItemRelatedRecord> CompletableFuture<Result<MultipleRecords<T>>> fetchItemsFor(
    Result<MultipleRecords<T>> result,
    BiFunction<T, Item, T> includeItemMap) {

    if (result.failed() || result.value().getRecords().isEmpty()) {
      return CompletableFuture.completedFuture(result);
    }

    return result.combineAfter(r -> fetchFor(getItemIds(r)),
      (records, items) -> new MultipleRecords<>(
        matchItemToRecord(records, items, includeItemMap),
        records.getTotalRecords()));
  }

  private CompletableFuture<Result<Collection<Item>>> fetchFor(
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
    Collection<Item> items,
    BiFunction<T, Item, T> includeItemMap) {

    return records.getRecords().stream()
      .map(r -> includeItemMap.apply(r,
        items.stream()
          .filter(item -> StringUtils.equals(item.getItemId(), r.getItemId()))
          .findFirst().orElse(Item.from(null))))
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<Item>> fetchItemRelatedRecords(
    Result<Item> item) {

    return fetchHoldingsRecord(item)
      .thenComposeAsync(this::fetchInstance)
      .thenComposeAsync(this::fetchLocation)
      .thenComposeAsync(this::fetchMaterialType);
  }
}
