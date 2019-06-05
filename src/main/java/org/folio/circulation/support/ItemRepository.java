package org.folio.circulation.support;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

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
  private final CollectionResourceClient loanTypesClient;
  private final LocationRepository locationRepository;
  private final MaterialTypeRepository materialTypeRepository;
  private final ServicePointRepository servicePointRepository;
  private final boolean fetchLocation;
  private final boolean fetchMaterialType;
  private final boolean fetchLoanType;

  public ItemRepository(
    Clients clients,
    boolean fetchLocation,
    boolean fetchMaterialType,
    boolean fetchLoanType) {

    this(clients.itemsStorage(),
      clients.holdingsStorage(),
      clients.instancesStorage(),
      clients.loanTypesStorage(),
      new LocationRepository(clients),
      new MaterialTypeRepository(clients),
      new ServicePointRepository(clients),
      fetchLocation, fetchMaterialType, fetchLoanType);
  }

  private ItemRepository(
    CollectionResourceClient itemsClient,
    CollectionResourceClient holdingsClient,
    CollectionResourceClient instancesClient,
    CollectionResourceClient loanTypesClient,
    LocationRepository locationRepository,
    MaterialTypeRepository materialTypeRepository,
    ServicePointRepository servicePointRepository,
    boolean fetchLocation,
    boolean fetchMaterialType,
    boolean fetchLoanType) {

    this.itemsClient = itemsClient;
    this.holdingsClient = holdingsClient;
    this.instancesClient = instancesClient;
    this.loanTypesClient = loanTypesClient;
    this.locationRepository = locationRepository;
    this.materialTypeRepository = materialTypeRepository;
    this.servicePointRepository = servicePointRepository;
    this.fetchLocation = fetchLocation;
    this.fetchMaterialType = fetchMaterialType;
    this.fetchLoanType = fetchLoanType;
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

  private CompletableFuture<Result<Item>> fetchLoanType(Result<Item> result) {
    if (!fetchLoanType) {
      return completedFuture(result);
    }
    return result.combineAfter(this::getLoanType, Item::withLoanType);
  }

  private CompletableFuture<Result<JsonObject>> getLoanType(Item item) {
    if (item.getItem() == null) {
      return completedFuture(succeeded(null));
    }
    return SingleRecordFetcher.json(loanTypesClient, "loan types",
      response -> succeeded(null))
      .fetch(item.determineLoanTypeForItem());
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

      final MultipleRecordFetcher<JsonObject> fetcher
        = new MultipleRecordFetcher<>(instancesClient, "instances", identity());

      return fetcher.findByIds(instanceIds)
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

      final MultipleRecordFetcher<JsonObject> fetcher
        = new MultipleRecordFetcher<>(holdingsClient, "holdingsRecords", identity());

      return fetcher.findByIds(holdingsIds)
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

    final MultipleRecordFetcher<Item> fetcher
      = new MultipleRecordFetcher<>(itemsClient, "items", Item::from);

    return fetcher.findByIds(itemIds)
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  private CompletableFuture<Result<Item>> fetchItem(String itemId) {
    return SingleRecordFetcher.jsonOrNull(itemsClient, "item")
      .fetch(itemId)
      .thenApply(r -> r.map(Item::from));
  }

  private CompletableFuture<Result<Item>> fetchItemByBarcode(String barcode) {
    log.info("Fetching item with barcode: {}", barcode);

    return CqlQuery.exactMatch("barcode", barcode)
       .after(query -> itemsClient.getMany(query, 1))
      .thenApply(result -> result.next(this::mapMultipleToResult))
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

  public CompletableFuture<Result<Collection<Item>>> findByQuery(Result<CqlQuery> queryResult) {
    MultipleRecordFetcher<Item> fetcher
      = new MultipleRecordFetcher<>(itemsClient, "items", Item::from);

    return fetcher.findByQuery(queryResult)
      .thenApply(mapResult(MultipleRecords::getRecords))
      .thenComposeAsync(this::fetchHoldingRecords)
      .thenComposeAsync(this::fetchInstances)
      .thenComposeAsync(this::fetchLocations)
      .thenComposeAsync(this::fetchMaterialTypes);
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
      .thenComposeAsync(this::fetchMaterialType)
      .thenComposeAsync(this::fetchLoanType);
  }
}
