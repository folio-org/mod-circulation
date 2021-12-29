package org.folio.circulation.infrastructure.storage.inventory;

import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria.byIndex;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.CommonResponseInterpreters.noContentRecordInterpreter;
import static org.folio.circulation.support.json.JsonKeys.byId;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.CollectionUtil.map;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemRelatedRecord;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.storage.mappers.HoldingsMapper;
import org.folio.circulation.storage.mappers.InstanceMapper;
import org.folio.circulation.storage.mappers.LoanTypeMapper;
import org.folio.circulation.storage.mappers.MaterialTypeMapper;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.fetching.RecordFetching;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class ItemRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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

  private static final String ITEMS_COLLECTION_PROPERTY_NAME = "items";

  public ItemRepository(org.folio.circulation.support.Clients clients,
    boolean fetchLocation, boolean fetchMaterialType, boolean fetchLoanType) {

    this(new Clients(clients.itemsStorage(), clients.holdingsStorage(),
      clients.instancesStorage(), clients.loanTypesStorage()), LocationRepository.using(clients),
      new MaterialTypeRepository(clients), new ServicePointRepository(clients),
      fetchLocation, fetchMaterialType, fetchLoanType);
  }

  private ItemRepository(Clients clients, LocationRepository locationRepository,
    MaterialTypeRepository materialTypeRepository, ServicePointRepository servicePointRepository,
    boolean fetchLocation, boolean fetchMaterialType, boolean fetchLoanType) {

    this.itemsClient = clients.getItemsClient();
    this.holdingsClient = clients.getHoldingsClient();
    this.instancesClient = clients.getInstancesClient();
    this.loanTypesClient = clients.getLoanTypesClient();
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
          .thenComposeAsync(this::fetchPrimaryServicePoint)
      : completedFuture(result);
  }

  private CompletableFuture<Result<Item>> fetchPrimaryServicePoint(Result<Item> itemResult) {
    return itemResult.combineAfter(item ->
      fetchPrimaryServicePoint(item.getLocation()), Item::withPrimaryServicePoint);
  }

  private CompletableFuture<Result<ServicePoint>> fetchPrimaryServicePoint(Location location) {
    if(isNull(location) || isNull(location.getPrimaryServicePointId())) {
      return ofAsync(() -> null);
    }

    return servicePointRepository.getServicePointById(
      location.getPrimaryServicePointId());
  }

  public CompletableFuture<Result<Item>> updateItem(Item item) {
    if (item == null) {
      return completedFuture(null);
    }

    return itemsClient.put(item.getItemId(), item.getItem())
      .thenApply(noContentRecordInterpreter(item)::flatMap)
      .thenCompose(x -> ofAsync(() -> item));
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

    return result.combineAfter(this::getLoanType,
      (item, newLoanTypeRepresentation) -> item.withLoanType(
        new LoanTypeMapper().toDomain(newLoanTypeRepresentation)));
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

    if (fetchLocation) {
      return result.after(items -> locationRepository.getAllItemLocations(items)
        .thenApply(r -> r.map(locations -> map(items, populateItemLocations(locations)))));
    } else {
      return completedFuture(result);
    }
  }

  private Function<Item, Item> populateItemLocations(Map<String, Location> locations) {
    return item -> {
      final Location permLocation = locations.get(item.getPermanentLocationId());
      final Location location = locations.get(item.getLocationId());

      return item.withLocation(location).withPermanentLocation(permLocation);
    };
  }

  private CompletableFuture<Result<Collection<Item>>> fetchMaterialTypes(
    Result<Collection<Item>> result) {

    final var mapper = new MaterialTypeMapper();

    if (fetchMaterialType) {
      return result.after(items ->
        materialTypeRepository.getMaterialTypes(items)
          .thenApply(r -> r.map(materialTypes -> items.stream()
              .map(item -> item.withMaterialType(mapper.toDomain(materialTypes
                .getOrDefault(item.getMaterialTypeId(), null))))
              .collect(Collectors.toList()))));
    }
    else {
      return completedFuture(result);
    }
  }

  private CompletableFuture<Result<Collection<Item>>> fetchLoanTypes(Result<Collection<Item>> result) {
    if (!fetchLoanType) {
      return completedFuture(result);
    }

    return result.after(items -> {
      Map<Item, String> itemToLoanTypeIdMap = items.stream()
        .collect(Collectors.toMap(identity(), Item::determineLoanTypeForItem));

      Set<String> loanTypeIdsToFetch = itemToLoanTypeIdMap.values().stream()
        .filter(StringUtils::isNoneBlank)
        .collect(Collectors.toSet());

      return findWithMultipleCqlIndexValues(loanTypesClient, "loantypes", identity())
        .findByIds(loanTypeIdsToFetch)
        .thenApply(mapResult(records -> records.toMap(byId())))
        .thenApply(flatMapResult(loanTypes -> matchLoanTypesToItems(itemToLoanTypeIdMap, loanTypes)));
    });
  }

  private Result<Collection<Item>> matchLoanTypesToItems(
    Map<Item, String> itemToLoanTypeId, Map<String, JsonObject> loanTypes) {

    final var mapper = new LoanTypeMapper();

    return succeeded(
      itemToLoanTypeId.entrySet().stream()
        .map(e -> e.getKey().withLoanType(
          mapper.toDomain(loanTypes.get(e.getValue()))))
        .collect(Collectors.toList()));
  }

  private CompletableFuture<Result<Collection<Item>>> fetchInstances(
    Result<Collection<Item>> result) {

    return result.after(items -> {
      List<String> instanceIds = items.stream()
        .map(Item::getInstanceId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());

      final FindWithMultipleCqlIndexValues<JsonObject> fetcher
        = findWithMultipleCqlIndexValues(instancesClient, "instances", identity());

      final var mapper = new InstanceMapper();

      return fetcher.findByIds(instanceIds)
        .thenApply(r -> r.map(instances -> items.stream()
          .map(item -> item.withInstance(mapper.toDomain(
              findById(item.getInstanceId(), instances.getRecords()).orElse(null))))
          .collect(Collectors.toList())));
    });
  }

  private CompletableFuture<Result<Collection<Item>>> fetchHoldingRecords(
    Result<Collection<Item>> result) {

    return result.after(items -> {
      List<String> holdingsIds = items.stream()
        .map(Item::getHoldingsRecordId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());

      final var fetcher
        = findWithMultipleCqlIndexValues(holdingsClient, "holdingsRecords", identity());

      final var mapper = new HoldingsMapper();

      return fetcher.findByIds(holdingsIds)
        .thenApply(r -> r.map(holdings -> items.stream()
          .map(item -> item.withHoldings(mapper.toDomain(
              findById(item.getHoldingsRecordId(), holdings.getRecords()).orElse(null))))
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

    final FindWithMultipleCqlIndexValues<Item> fetcher
      = findWithMultipleCqlIndexValues(itemsClient, ITEMS_COLLECTION_PROPERTY_NAME,
        Item::from);

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
       .after(query -> itemsClient.getMany(query, PageLimit.one()))
      .thenApply(result -> result.next(this::mapMultipleToResult))
      .thenApply(r -> r.map(Item::from))
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  private Result<JsonObject> mapMultipleToResult(Response response) {
    return MultipleRecords.from(response, identity(), ITEMS_COLLECTION_PROPERTY_NAME )
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
        final var mapper = new HoldingsMapper();

        return SingleRecordFetcher.json(holdingsClient, "holding",
            r -> failedValidation("Holding does not exist", ITEM_ID, item.getItemId()))
          .fetch(item.getHoldingsRecordId())
          .thenApply(r -> r.map(mapper::toDomain))
          .thenApply(r -> r.map(item::withHoldings));
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
        final var mapper = new InstanceMapper();

        return SingleRecordFetcher.jsonOrNull(instancesClient, "instance")
          .fetch(item.getInstanceId())
          .thenApply(r -> r.map(mapper::toDomain))
          .thenApply(r -> r.map(item::withInstance));
      }
    });
  }

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
    FindWithCqlQuery<Item> fetcher = RecordFetching.findWithCqlQuery(itemsClient, ITEMS_COLLECTION_PROPERTY_NAME, Item::from);

    return fetcher.findByQuery(queryResult)
      .thenApply(mapResult(MultipleRecords::getRecords))
      .thenComposeAsync(this::fetchHoldingRecords)
      .thenComposeAsync(this::fetchInstances)
      .thenComposeAsync(this::fetchLocations)
      .thenComposeAsync(this::fetchMaterialTypes);
  }

  public CompletableFuture<Result<Collection<Item>>> findByIndexNameAndQuery(
    Collection<String> ids, String indexName, Result<CqlQuery> query) {

    FindWithMultipleCqlIndexValues<Item> fetcher
      = findWithMultipleCqlIndexValues(itemsClient,
        ITEMS_COLLECTION_PROPERTY_NAME, Item::from);

    return fetcher.find(byIndex(indexName, ids).withQuery(query))
      .thenApply(mapResult(MultipleRecords::getRecords))
      .thenComposeAsync(this::fetchHoldingRecords)
      .thenComposeAsync(this::fetchInstances)
      .thenComposeAsync(this::fetchLocations)
      .thenComposeAsync(this::fetchMaterialTypes)
      .thenComposeAsync(this::fetchLoanTypes);
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

  public CompletableFuture<Result<Item>> fetchItemRelatedRecords(
    Result<Item> item) {

    return fetchHoldingsRecord(item)
      .thenComposeAsync(this::fetchInstance)
      .thenComposeAsync(this::fetchLocation)
      .thenComposeAsync(this::fetchMaterialType)
      .thenComposeAsync(this::fetchLoanType);
  }

  public static ItemRepository noLocationMaterialTypeAndLoanTypeInstance(org.folio.circulation.support.Clients clients) {
    return new ItemRepository(clients, false, false, false);
  }

  @AllArgsConstructor
  @Getter
  private static class Clients {
    private final CollectionResourceClient itemsClient;
    private final CollectionResourceClient holdingsClient;
    private final CollectionResourceClient instancesClient;
    private final CollectionResourceClient loanTypesClient;
  }
}
