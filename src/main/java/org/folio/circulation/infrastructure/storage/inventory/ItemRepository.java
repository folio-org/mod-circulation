package org.folio.circulation.infrastructure.storage.inventory;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
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
      new LoanTypeRepository(clients.loanTypesStorage()), clients.circulationItemClient());
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
    write(updatedItemRepresentation, IN_TRANSIT_DESTINATION_SERVICE_POINT_ID,
      item.getInTransitDestinationServicePointId());

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
    return fetchItemByBarcode(barcode)
      .thenComposeAsync(itemResult -> itemResult.after(when(item -> ofAsync(item::isNotFound),
        item -> fetchCirculationItemByBarcode(barcode), item -> completedFuture(itemResult))))
      .thenComposeAsync(this::fetchItemRelatedRecords);
  }

  private CompletableFuture<Result<Item>> fetchCirculationItemByBarcode(String barcode) {
    final var mapper = new ItemMapper();

    return SingleRecordFetcher.jsonOrNull(circulationItemClient, "item")
      .fetchWithQueryStringParameters(Map.of("barcode", barcode))
      .thenApply(mapResult(identityMap::add))
      .thenApply(r -> r.map(mapper::toDomain));
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
        .combineRecords(locations, matchRecordsById(Item::getPermanentLocationId, Location::getId),
          Item::withPermanentLocation, null)
        .combineRecords(locations, matchRecordsById(Item::getEffectiveLocationId, Location::getId),
          Item::withLocation, null));
  }

  private CompletableFuture<Result<MultipleRecords<Location>>> fetchLocations(
    MultipleRecords<Item> items) {

    final var locationIds = items.toKeys(Item::getEffectiveLocationId);
    final var permanentLocationIds = items.toKeys(Item::getPermanentLocationId);

    final var allLocationIds = new HashSet<String>();

    allLocationIds.addAll(locationIds);
    allLocationIds.addAll(permanentLocationIds);

    return locationRepository.fetchLocations(allLocationIds);
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchMaterialTypes(
    Result<MultipleRecords<Item>> result) {

    return result.after(items ->
      materialTypeRepository.getMaterialTypes(items)
        .thenApply(mapResult(materialTypes -> items.combineRecords(materialTypes,
          matchRecordsById(Item::getMaterialTypeId, MaterialType::getId),
          Item::withMaterialType, MaterialType.unknown()))));
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchLoanTypes(
    Result<MultipleRecords<Item>> result) {

    return result.after(items -> {
      final var loanTypeIdsToFetch = items.toKeys(Item::getLoanTypeId);

      return loanTypeRepository.findByIds(loanTypeIdsToFetch)
        .thenApply(mapResult(loanTypes -> items.combineRecords(loanTypes,
          matchRecordsById(Item::getLoanTypeId, LoanType::getId),
          Item::withLoanType, LoanType.unknown())));
    });
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchInstances(
    Result<MultipleRecords<Item>> result) {

    return result.after(items -> {
      final var instanceIds = items.toKeys(Item::getInstanceId);

      return instanceRepository.fetchByIds(instanceIds)
        .thenApply(mapResult(instances -> items.combineRecords(instances,
          matchRecordsById(Item::getInstanceId, Instance::getId),
          Item::withInstance, Instance.unknown())));
    });
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchHoldingsRecords(
    Result<MultipleRecords<Item>> result) {

    return result.after(items -> {
      final var holdingsIds = items.toKeys(Item::getHoldingsRecordId);

      return holdingsRepository.fetchByIds(holdingsIds)
        .thenApply(mapResult(holdings -> items.combineRecords(holdings,
          matchRecordsById(Item::getHoldingsRecordId, Holdings::getId),
          Item::withHoldings, Holdings.unknown())));
    });
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchItems(Collection<String> itemIds) {
    final var finder = new CqlIndexValuesFinder<>(createItemFinder());
    final var mapper = new ItemMapper();

    return finder.findByIds(itemIds)
      .thenApply(mapResult(identityMap::add))
      .thenApply(mapResult(records -> records.mapRecords(mapper::toDomain)));
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

  private CompletableFuture<Result<Item>> fetchItemByBarcode(String barcode) {
    log.info("Fetching item with barcode: {}", barcode);

    final var finder = createItemFinder();
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
}
