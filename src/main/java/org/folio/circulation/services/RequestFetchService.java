package org.folio.circulation.services;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.ObjectUtils.allNull;
import static org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria.byIndex;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestLevel;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.infrastructure.storage.inventory.HoldingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.InstanceRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.resources.context.StaffSlipsContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.fetching.CqlIndexValuesFinder;
import org.folio.circulation.support.fetching.CqlQueryFinder;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

public class RequestFetchService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String ITEM_EFFECTIVE_LOCATION_ID_KEY = "item.itemEffectiveLocationId";
  private static final String STATUS_KEY = "status";
  private static final String REQUESTS_KEY = "requests";
  private static final String REQUEST_TYPE_KEY = "requestType";
  private static final String REQUEST_LEVEL_KEY = "requestLevel";
  private static final int REQUESTS_LIMIT = 1000;

  private final RequestType requestType;
  private final InstanceRepository instanceRepository;
  private final HoldingsRepository holdingsRepository;
  private final ItemRepository itemRepository;
  private final LocationRepository locationRepository;
  private final CollectionResourceClient requestStorageClient;

  public RequestFetchService(Clients clients, RequestType requestType) {
    this.requestType = requestType;
    this.instanceRepository = new InstanceRepository(clients);
    this.holdingsRepository = new HoldingsRepository(clients.holdingsStorage());
    this.itemRepository = new ItemRepository(clients);
    this.locationRepository = LocationRepository.using(clients);
    this.requestStorageClient = clients.requestsStorage();
  }

  public CompletableFuture<Result<StaffSlipsContext>> fetchRequests(StaffSlipsContext context) {
    return fetchRequestsWithItems(context)
      .thenCompose(r -> r.after(this::fetchRequestsWithoutItems));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchRequestsWithItems(
    StaffSlipsContext context) {

    MultipleRecords<Location> locations = context.getLocations();
    if (locations.isEmpty()) {
      log.info("fetchRequestsWithItems:: no locations to search requests for");
      return ofAsync(context.withRequests(MultipleRecords.empty()));
    }

    var query = exactMatch(REQUEST_TYPE_KEY, requestType.getValue())
      .combine(exactMatch(STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue()), CqlQuery::and);
    var criteria = byIndex(ITEM_EFFECTIVE_LOCATION_ID_KEY, locations.toKeys(Location::getId))
      .withQuery(query);

    log.info("fetchRequestsWithItems:: fetching requests by item location with limit {}", REQUESTS_LIMIT);

//    return findWithMultipleCqlIndexValues(requestStorageClient, REQUESTS_KEY, Request::from)
    return new CqlIndexValuesFinder<>(new CqlQueryFinder<>(requestStorageClient, REQUESTS_KEY, Request::from), 70)
      .find(criteria, REQUESTS_LIMIT)
      .thenCompose(r -> r.after(requests -> fetchItemsForRequests(requests, locations)))
      .thenApply(r -> r.map(context::withRequests));
  }

  private CompletableFuture<Result<MultipleRecords<Request>>> fetchItemsForRequests(
    MultipleRecords<Request> requests, MultipleRecords<Location> locations) {

    Set<String> itemIds = requests.getRecords()
      .stream()
      .map(Request::getItemId)
      .filter(Objects::nonNull)
      .collect(toSet());

    if (itemIds.isEmpty()) {
      log.info("fetchItems:: no items to search");
      return ofAsync(requests);
    }

    return itemRepository.fetchFor(itemIds)
      .thenCompose(r -> r.after(items -> fetchLocationDetailsForItems(items, locations)))
      .thenApply(r -> r.map(items -> mapItemsToRequests(items, requests)));
  }

  private CompletableFuture<Result<Collection<Item>>> fetchLocationDetailsForItems(
    MultipleRecords<Item> items, MultipleRecords<Location> locations) {

    log.debug("fetchLocationDetailsForItems:: parameters items: {}",
      () -> multipleRecordsAsString(items));

    Set<String> locationIdsFromItems = items.toKeys(Item::getEffectiveLocationId);
    Set<Location> locationsForItems = locations.getRecords()
      .stream()
      .filter(location -> locationIdsFromItems.contains(location.getId()))
      .collect(toSet());

    if (locationsForItems.isEmpty()) {
      log.info("fetchLocationDetailsForItems:: locationsForItems is empty");

      return ofAsync(emptyList());
    }

    return ofAsync(locationsForItems)
      .thenComposeAsync(r -> r.after(locationRepository::fetchLibraries))
      .thenComposeAsync(r -> r.after(locationRepository::fetchInstitutions))
      .thenComposeAsync(r -> r.after(locationRepository::fetchCampuses))
      .thenApply(r -> r.map(updatedLocations -> matchLocationsToItems(items, updatedLocations)));
  }

  private Collection<Item> matchLocationsToItems(
    MultipleRecords<Item> items, Collection<Location> locations) {

    log.debug("matchLocationsToItems:: parameters items: {}, locations: {}",
      () -> multipleRecordsAsString(items), () -> collectionAsString(locations));

    Map<String, Location> locationsMap = locations.stream()
      .collect(toMap(Location::getId, identity(), (a, b) -> a));

    return items.mapRecords(item -> item.withLocation(
        locationsMap.getOrDefault(item.getEffectiveLocationId(),
          Location.unknown(item.getEffectiveLocationId()))))
      .getRecords();
  }

  private static MultipleRecords<Request> mapItemsToRequests(Collection<Item> items,
    MultipleRecords<Request> requests) {

    Map<String, Item> itemMap = items.stream()
      .collect(toMap(Item::getItemId, identity(), (a, b) -> a));

    return requests.mapRecords(request -> request.withItem(itemMap.get(request.getItemId())));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchRequestsWithoutItems(
    StaffSlipsContext context) {

    log.info("fetchRequestsWithoutItems:: requestType={}", requestType);
    if (requestType != RequestType.HOLD) {
      return ofAsync(context);
    }

    int requestsToFetch = REQUESTS_LIMIT - context.getRequests().size();
    if (requestsToFetch <= 0) {
      log.info("fetchRequestsWithoutItems:: requests limit reached, doing nothing");
      return ofAsync(context);
    }

    return fetchRequestsWithoutItems()
      .thenCompose(r -> r.after(requests -> processRequestsWithoutItems(context, requests)));
  }

  private CompletableFuture<Result<MultipleRecords<Request>>> fetchRequestsWithoutItems() {
    var query = exactMatch(REQUEST_TYPE_KEY, RequestType.HOLD.getValue())
      .combine(exactMatch(STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue()), CqlQuery::and)
      .combine(exactMatch(REQUEST_LEVEL_KEY, RequestLevel.TITLE.getValue()), CqlQuery::and);

    return findWithCqlQuery(requestStorageClient, REQUESTS_KEY, Request::from)
      .findByQuery(query, PageLimit.limit(REQUESTS_LIMIT))
      // Title-level holds in status "Open - Not yet filled" are not supposed to be linked to items,
      // but we need to double-check anyway. These requests can be filtered out at DB level using
      // CQL predicate 'not itemId=""', but CQL parser used in tests does not seem to support this construct.
      .thenApply(mapResult(this::filterOutRequestsWithItems));
  }

  private MultipleRecords<Request> filterOutRequestsWithItems(MultipleRecords<Request> requests) {
    return requests.filter(r -> allNull(r.getHoldingsRecordId(), r.getItemId()));
  }

  private CompletableFuture<Result<StaffSlipsContext>> processRequestsWithoutItems(
    StaffSlipsContext context, MultipleRecords<Request> requestsWithoutItems) {

    log.info("processRequestsWithoutItems:: requests: {}", requestsWithoutItems::size);
    if (requestsWithoutItems.isEmpty()) {
      return ofAsync(context);
    }

    return fetchInstances(requestsWithoutItems)
      .thenCompose(r -> r.after(requests -> fetchHoldings(requests)
        .thenApply(rr -> rr.map(holdings -> addRelevantRequestsToContext(context, holdings, requests)))));
  }

  private CompletableFuture<Result<MultipleRecords<Request>>> fetchInstances(
    MultipleRecords<Request> requests) {

    return instanceRepository.fetchByRequests(requests)
      .thenApply(r -> r.map(instances -> mapInstancesToRequests(requests, instances)));
  }

  private CompletableFuture<Result<MultipleRecords<Holdings>>> fetchHoldings(
    MultipleRecords<Request> requests) {

    Set<String> instanceIds = requests.getRecords()
      .stream()
      .map(Request::getInstanceId)
      .collect(toSet());

    return holdingsRepository.fetchByInstances(instanceIds);
  }

  private StaffSlipsContext addRelevantRequestsToContext(StaffSlipsContext context,
    MultipleRecords<Holdings> holdings, MultipleRecords<Request> requests) {

    List<String> relevantLocationIds = context.getLocations()
      .getRecords()
      .stream()
      .map(Location::getId)
      .toList();

    Set<String> relevantInstanceIds = holdings.getRecords()
      .stream()
      .filter(holding -> relevantLocationIds.contains(holding.getEffectiveLocationId()))
      .map(Holdings::getInstanceId)
      .collect(toSet());

    MultipleRecords<Request> relevantRequests = requests.filter(
      request -> relevantInstanceIds.contains(request.getInstanceId()));

    log.info("addRelevantRequestsToContext:: found {} locations, {} instances, {} requests",
      relevantLocationIds::size, relevantInstanceIds::size, relevantRequests::size);

    return relevantRequests.isEmpty()
      ? context
      : context.withRequests(context.getRequests().combine(relevantRequests));
  }


  private MultipleRecords<Request> mapInstancesToRequests(MultipleRecords<Request> requests,
    MultipleRecords<Instance> instances) {

    Map<String, Instance> instancesById = instances.getRecordsMap(Instance::getId);
    return requests.mapRecords(r -> r.withInstance(instancesById.get(r.getInstanceId())));
  }

}
