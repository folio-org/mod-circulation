package org.folio.circulation.infrastructure.storage.inventory;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.function.Function.identity;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.domain.MultipleRecords.CombinationMatchers.matchRecordsById;
import static org.folio.circulation.domain.representations.ItemProperties.LAST_CHECK_IN;
import static org.folio.circulation.domain.representations.ItemProperties.STATUS_PROPERTY;
import static org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria.byIndex;
import static org.folio.circulation.support.http.CommonResponseInterpreters.noContentRecordInterpreter;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.PageLimit.one;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.remove;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.results.AsynchronousResultBindings.combineAfter;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemRelatedRecord;
import org.folio.circulation.domain.LoanType;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MaterialType;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.IdentityMap;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.storage.mappers.ItemMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.fetching.CqlIndexValuesFinder;
import org.folio.circulation.support.fetching.CqlQueryFinder;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ItemRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient itemsClient;
  private final LocationRepository locationRepository;
  private final MaterialTypeRepository materialTypeRepository;
  private final InstanceRepository instanceRepository;
  private final HoldingsRepository holdingsRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final CollectionResourceClient circulationItemClient;
  private final IdentityMap identityMap = new IdentityMap(
    item -> getProperty(item, "id"));

  public ItemRepository(Clients clients) {
    this(clients.itemsStorage(), LocationRepository.using(clients,
        new ServicePointRepository(clients)),
      new MaterialTypeRepository(clients), new InstanceRepository(clients),
      new HoldingsRepository(clients.holdingsStorage()),
      new LoanTypeRepository(clients.loanTypesStorage()),
      clients.circulationItemClient());
  }

  public CompletableFuture<Result<Item>> fetchFor(ItemRelatedRecord itemRelatedRecord) {
    log.debug("fetchFor:: itemRelatedRecord: {}", itemRelatedRecord);
    if (itemRelatedRecord.getItemId() == null) {
      log.info("fetchFor:: item id is null");
      return completedFuture(succeeded(Item.from(null)));
    }

    return fetchById(itemRelatedRecord.getItemId());
  }

  public CompletableFuture<Result<Item>> updateItem(Item item) {
    log.debug("updateItem:: parameters item: {}", item);

    final String IN_TRANSIT_DESTINATION_SERVICE_POINT_ID = "inTransitDestinationServicePointId";
    final String TEMPORARY_LOCATION_ID = "temporaryLocationId";

    if (item == null) {
      log.info("updateItem:: item is null");
      return ofAsync(() -> null);
    }

    if (identityMap.entryNotPresent(item.getItemId())) {
      return completedFuture(Result.failed(new ServerErrorFailure(
        "Cannot update item when original representation is not available in identity map")));
    }

    final var updatedItemRepresentation = identityMap.get(item.getItemId());

    write(updatedItemRepresentation, STATUS_PROPERTY,
      new JsonObject().put("name", item.getStatus().getValue()));

    remove(updatedItemRepresentation, IN_TRANSIT_DESTINATION_SERVICE_POINT_ID);
    if (item.isInStatus(IN_TRANSIT)) {
      write(updatedItemRepresentation, IN_TRANSIT_DESTINATION_SERVICE_POINT_ID,
        item.getInTransitDestinationServicePointId());
    } else if (item.isInStatus(AVAILABLE) && item.canFloatThroughCheckInServicePoint()) {
      remove(updatedItemRepresentation, TEMPORARY_LOCATION_ID);
      write(updatedItemRepresentation, TEMPORARY_LOCATION_ID,
        item.getFloatDestinationLocationId());
    }

    final var lastCheckIn = item.getLastCheckIn();

    if (lastCheckIn == null) {
      remove(updatedItemRepresentation, LAST_CHECK_IN);
    }
    else {
      write(updatedItemRepresentation, LAST_CHECK_IN, lastCheckIn.toJson());
    }

    return (item.isDcbItem() ? circulationItemClient : itemsClient)
      .put(item.getItemId(), updatedItemRepresentation)
      .thenApply(noContentRecordInterpreter(item)::flatMap)
      .thenCompose(x -> ofAsync(() -> item));
  }

  public CompletableFuture<Result<Item>> getFirstAvailableItemByInstanceId(String instanceId) {
    return holdingsRepository.fetchByInstanceId(instanceId)
      .thenCompose(r -> r.after(this::getAvailableItem));
  }

  private CompletableFuture<Result<Item>> getAvailableItem(
    MultipleRecords<Holdings> holdingsRecords) {
    log.debug("getAvailableItem:: parameters: holdingsRecords: {}", () -> multipleRecordsAsString(holdingsRecords));

    if (holdingsRecords == null || holdingsRecords.isEmpty()) {
      log.info("getAvailableItem:: holdingsRecords is null or empty");
      return ofAsync(() -> Item.from(null));
    }

    return findByIndexNameAndQuery(holdingsRecords.toKeys(Holdings::getId),
      "holdingsRecordId", exactMatch("status.name", AVAILABLE.getValue()))
      .thenApply(mapResult(MultipleRecords::firstOrNull));
  }

  public CompletableFuture<Result<Item>> fetchByBarcode(String barcode) {
    return fetchItemByBarcode(barcode, createItemFinder())
      .thenComposeAsync(itemResult -> itemResult.after(when(item -> ofAsync(item::isNotFound),
        item -> fetchItemByBarcode(barcode, createCirculationItemFinder())
          .thenApply(r -> r.mapFailure(failure -> Result.succeeded(item)))
        , item -> completedFuture(itemResult))))
      .thenComposeAsync(this::fetchItemRelatedRecords);
  }

  public CompletableFuture<Result<Item>> fetchById(String itemId) {
    return fetchItem(itemId)
      .thenComposeAsync(itemResult -> itemResult.after(when(item -> ofAsync(item::isNotFound),
        item -> fetchCirculationItem(itemId), item -> completedFuture(itemResult))))
      .thenComposeAsync(this::fetchItemRelatedRecords);
  }

  private CompletableFuture<Result<Item>> fetchCirculationItem(String id) {
    final var mapper = new ItemMapper();

    return SingleRecordFetcher.jsonOrNull(circulationItemClient, "item")
      .fetch(id)
      .thenApply(mapResult(identityMap::add))
      .thenApply(r -> r.map(mapper::toDomain));
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchLocations(
    Result<MultipleRecords<Item>> result) {

    return result.combineAfter(this::fetchLocations,
      (items, locations) -> items
        .combineRecords(locations, Item::getPermanentLocationId, Item::withPermanentLocation, null)
        .combineRecords(locations, Item::getEffectiveLocationId, Item::withLocation, null)
        .combineRecords(locations, Item::getFloatDestinationLocationId, Item::withFloatDestinationLocation, null));
  }

  private CompletableFuture<Result<Map<String, Location>>> fetchLocations(
    MultipleRecords<Item> items) {

    final var locationIds = items.toKeys(Item::getEffectiveLocationId);
    final var permanentLocationIds = items.toKeys(Item::getPermanentLocationId);

    final var allLocationIds = new HashSet<String>();

    allLocationIds.addAll(locationIds);
    allLocationIds.addAll(permanentLocationIds);

    return locationRepository.fetchLocations(allLocationIds)
      .thenApply(r -> r.map(records -> records.getRecordsMap(Location::getId)));
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchMaterialTypes(
    Result<MultipleRecords<Item>> result) {

    return supplyAsync(() -> result.after(items -> materialTypeRepository.getMaterialTypes(items)
        .thenApply(r -> r.map(records -> records.getRecordsMap(MaterialType::getId)))
        .thenApply(mapResult(materialTypes -> items.combineRecords(materialTypes,
          Item::getMaterialTypeId, Item::withMaterialType, MaterialType.unknown())))))
      .thenCompose(Function.identity());
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchLoanTypes(
    Result<MultipleRecords<Item>> result) {

    return result.after(items -> {
      final var loanTypeIdsToFetch = items.toKeys(Item::getLoanTypeId);

      return supplyAsync(() -> loanTypeRepository.findByIds(loanTypeIdsToFetch)
        .thenApply(r -> r.map(records -> records.getRecordsMap(LoanType::getId)))
        .thenApply(mapResult(loanTypes -> items.combineRecords(loanTypes,
          Item::getLoanTypeId, Item::withLoanType, LoanType.unknown()))))
        .thenCompose(Function.identity());
    });
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchInstances(
    Result<MultipleRecords<Item>> result) {

    return result.after(items -> {
      final var instanceIds = items.toKeys(Item::getInstanceId);

      return supplyAsync(() -> instanceRepository.fetchByIds(instanceIds)
        .thenApply(r -> r.map(records -> records.getRecordsMap(Instance::getId)))
        .thenApply(mapResult(instances -> items.combineRecords(instances,
          Item::getInstanceId, Item::withInstance, Instance.unknown()))))
        .thenCompose(Function.identity());
    });
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchHoldingsRecords(
    Result<MultipleRecords<Item>> result) {

    return result.after(items -> {
      final var holdingsIds = items.toKeys(Item::getHoldingsRecordId);

      return supplyAsync(() -> holdingsRepository.fetchByIds(holdingsIds)
        .thenApply(r -> r.map(records -> records.getRecordsMap(Holdings::getId)))
        .thenApply(mapResult(holdings -> items.combineRecords(holdings,
          Item::getHoldingsRecordId, Item::withHoldings, Holdings.unknown()))))
        .thenCompose(Function.identity());
    });
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchItems(Collection<String> itemIds) {
    final var finder = new CqlIndexValuesFinder<>(createItemFinder());
    final var mapper = new ItemMapper();

    return finder.findByIds(itemIds)
      .thenApply(mapResult(identityMap::add))
      .thenApply(mapResult(records -> records.mapRecords(mapper::toDomain)))
      .thenCompose(res -> res.value().getTotalRecords() == itemIds.size()
        ? CompletableFuture.completedFuture(res)
        : res.combineAfter(x -> lookupDcbItem(res, itemIds), MultipleRecords::combine));
  }

  private CompletableFuture<Result<MultipleRecords<Item>>>
    lookupDcbItem(Result<MultipleRecords<Item>> inventoryItems, Collection<String> itemIds) {
    log.debug("lookupDcbItem:: Looking up for DCB items");

    var inventoryItemIds = inventoryItems.value().toKeys(Item::getItemId);
    final var finder = new CqlIndexValuesFinder<>(createCirculationItemFinder());
    var dcbItemIds = itemIds.stream().filter(ids -> !inventoryItemIds.contains(ids)).toList();
    final var mapper = new ItemMapper();

    return finder.findByIds(dcbItemIds)
      .thenApply(mapResult(identityMap::add))
      .thenApply(r -> r.map(records -> records.mapRecords(mapper::toDomain)))
      .thenApply(r -> r.mapFailure(failure -> succeeded(MultipleRecords.empty())));
  }

  private CompletableFuture<Result<Item>> fetchItem(String itemId) {
    final var mapper = new ItemMapper();

    return fetchItemAsJson(itemId)
      .thenApply(mapResult(mapper::toDomain));
  }

  public CompletableFuture<Result<JsonObject>> fetchItemAsJson(String itemId) {

    return SingleRecordFetcher.jsonOrNull(itemsClient, "item")
      .fetch(itemId)
      .thenApply(mapResult(identityMap::add));
  }

  private CompletableFuture<Result<Item>> fetchItemByBarcode(String barcode, CqlQueryFinder<JsonObject> finder) {
    log.info("Fetching item with barcode: {}", barcode);

    final var mapper = new ItemMapper();

    return finder.findByQuery(exactMatch("barcode", barcode), one())
      .thenApply(records -> records.map(MultipleRecords::firstOrNull))
      .thenApply(mapResult(identityMap::add))
      .thenApply(mapResult(mapper::toDomain));
  }

  public <T extends ItemRelatedRecord> CompletableFuture<Result<MultipleRecords<T>>>
  fetchItemsFor(Result<MultipleRecords<T>> result, BiFunction<T, Item, T> includeItemMap) {

    return fetchItemsFor(result, includeItemMap, this::fetchFor);
  }

  public <T extends ItemRelatedRecord> CompletableFuture<Result<MultipleRecords<T>>>
  fetchItemsWithHoldings(Result<MultipleRecords<T>> result, BiFunction<T, Item, T> withItemMapper) {

    return fetchItemsFor(result, withItemMapper, this::fetchItems);
  }

  public <T extends ItemRelatedRecord> CompletableFuture<Result<MultipleRecords<T>>>
  fetchItems(Result<MultipleRecords<T>> result) {

    return fetchItemsFor(result, (itemRelatedRecord, item) -> (T) itemRelatedRecord.withItem(item),
      this::fetchItems);
  }

  public <T extends ItemRelatedRecord> CompletableFuture<Result<MultipleRecords<T>>>
  fetchItemsFor(Result<MultipleRecords<T>> result, BiFunction<T, Item, T> includeItemMap,
    Function<Collection<String>, CompletableFuture<Result<MultipleRecords<Item>>>> fetcher) {

    return result.combineAfter(
      r -> fetcher.apply(r.toKeys(ItemRelatedRecord::getItemId)),
      (records, items) -> records
        .combineRecords(
          items, matchRecordsById(ItemRelatedRecord::getItemId, Item::getItemId),
          includeItemMap, Item.from(null)));
  }

  public CompletableFuture<Result<Collection<Item>>> findBy(String indexName, Collection<String> ids) {
    final var finder = new CqlIndexValuesFinder<>(createItemFinder());
    final var mapper = new ItemMapper();

    return finder.find(byIndex(indexName, ids))
      .thenApply(mapResult(identityMap::add))
      .thenApply(mapResult(m -> m.mapRecords(mapper::toDomain)))
      .thenComposeAsync(this::fetchItemsRelatedRecords)
      .thenApply(mapResult(MultipleRecords::getRecords));
  }

  public CompletableFuture<Result<MultipleRecords<Holdings>>> findHoldingsByIds(
    Collection<String> holdingsRecordIds) {

    return holdingsRepository.fetchByIds(holdingsRecordIds);
  }

  public CompletableFuture<Result<MultipleRecords<Item>>> findByIndexNameAndQuery(
    Collection<String> ids, String indexName, Result<CqlQuery> query) {

    final var finder = new CqlIndexValuesFinder<>(createItemFinder());
    final var mapper = new ItemMapper();

    return finder.find(byIndex(indexName, ids).withQuery(query))
      .thenApply(mapResult(identityMap::add))
      .thenApply(mapResult(m -> m.mapRecords(mapper::toDomain)))
      .thenComposeAsync(this::fetchItemsRelatedRecords);
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchFor(
    Collection<String> itemIds) {

    return fetchItems(itemIds)
      .thenComposeAsync(this::fetchItemsRelatedRecords);
  }

  public CompletableFuture<Result<Item>> fetchItemRelatedRecords(Result<Item> itemResult) {
    return itemResult.combineAfter(this::fetchHoldingsRecord, Item::withHoldings)
      .thenComposeAsync(combineAfter(this::fetchInstance, Item::withInstance))
      .thenComposeAsync(combineAfter(locationRepository::getEffectiveLocation, Item::withLocation))
      .thenComposeAsync(combineAfter(materialTypeRepository::getFor, Item::withMaterialType))
      .thenComposeAsync(combineAfter(this::fetchLoanType, Item::withLoanType));
  }

  private CompletableFuture<Result<Holdings>> fetchHoldingsRecord(Item item) {
    if (item == null || item.isNotFound()) {
      log.info("Item was not found, aborting fetching holding or instance");
      return ofAsync(Holdings::unknown);
    }
    else {
      return holdingsRepository.fetchById(item.getHoldingsRecordId());
    }
  }

  private CompletableFuture<Result<Instance>> fetchInstance(Item item) {
    if (item == null || item.isNotFound() || item.getInstanceId() == null) {
      log.info("Holding was not found, aborting fetching instance");
      return ofAsync(Instance::unknown);
    } else {
      return instanceRepository.fetchById(item.getInstanceId());
    }
  }

  private CompletableFuture<Result<LoanType>> fetchLoanType(Item item) {
    if (item.getLoanTypeId() == null) {
      return completedFuture(succeeded(LoanType.unknown()));
    }

    return loanTypeRepository.fetchById(item.getLoanTypeId());
  }

  public CompletableFuture<Result<MultipleRecords<Item>>> fetchItemsRelatedRecords(
    Result<MultipleRecords<Item>> items) {

    return fetchHoldingsRecords(items)
      .thenComposeAsync(this::fetchInstances)
      .thenComposeAsync(this::fetchLocations)
      .thenComposeAsync(this::fetchMaterialTypes)
      .thenComposeAsync(this::fetchLoanTypes);
  }

  private CqlQueryFinder<JsonObject> createItemFinder() {
    return new CqlQueryFinder<>(itemsClient, "items", identity());
  }

  private CqlQueryFinder<JsonObject> createCirculationItemFinder() {
    return new CqlQueryFinder<>(circulationItemClient, "items", identity());
  }
}
