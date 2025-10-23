package org.folio.circulation.services;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.ObjectUtils.allNull;
import static org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria.byIndex;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.PageLimit.maximumLimit;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
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
import org.folio.circulation.resources.context.StaffSlipsContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

public class RequestFetchService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String ITEM_ID_KEY = "itemId";
  private static final String STATUS_KEY = "status";
  private static final String REQUESTS_KEY = "requests";
  private static final String REQUEST_TYPE_KEY = "requestType";
  private static final String REQUEST_LEVEL_KEY = "requestLevel";

  private final RequestType requestType;
  private final InstanceRepository instanceRepository;
  private final HoldingsRepository holdingsRepository;
  private final CollectionResourceClient requestStorageClient;

  public RequestFetchService(Clients clients, RequestType requestType) {
    this.requestType = requestType;
    this.instanceRepository = new InstanceRepository(clients);
    this.holdingsRepository = new HoldingsRepository(clients.holdingsStorage());
    this.requestStorageClient = clients.requestsStorage();
  }

  public CompletableFuture<Result<StaffSlipsContext>> fetchRequests(StaffSlipsContext context) {
    return fetchRequestsWithItems(context)
      .thenCompose(r -> r.after(this::fetchRequestsWithoutItems));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchRequestsWithItems(
    StaffSlipsContext context) {

    Collection<Item> items = context.getItems();
    if (items == null || items.isEmpty()) {
      log.info("fetchOpenPageRequestsForItems:: no items fetched");

      return ofAsync(context.withRequests(MultipleRecords.empty()));
    }
    Set<String> itemIds = items.stream()
      .map(Item::getItemId)
      .filter(StringUtils::isNoneBlank)
      .collect(toSet());

    if (itemIds.isEmpty()) {
      log.info("fetchOpenPageRequestsForItems:: itemIds is empty");

      return ofAsync(context.withRequests(MultipleRecords.empty()));
    }

    var query = exactMatch(REQUEST_TYPE_KEY, requestType.getValue())
      .combine(exactMatch(STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue()), CqlQuery::and);

    return findWithMultipleCqlIndexValues(requestStorageClient, REQUESTS_KEY, Request::from)
      .find(byIndex(ITEM_ID_KEY, itemIds).withQuery(query))
      .thenApply(flatMapResult(requests -> matchItemsToRequests(requests, items)))
      .thenApply(r -> r.map(context::withRequests));
  }

  private Result<MultipleRecords<Request>> matchItemsToRequests(
    MultipleRecords<Request> requests, Collection<Item> items) {

    Map<String, Item> itemMap = items.stream()
      .collect(toMap(Item::getItemId, identity(), (a, b) -> a));

    return succeeded(requests.mapRecords(request -> request.withItem(
      itemMap.getOrDefault(request.getItemId(), null))));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchRequestsWithoutItems(
    StaffSlipsContext context) {

    log.info("fetchRequestsWithoutItems:: requestType={}", requestType);
    if (requestType != RequestType.HOLD) {
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
      .findByQuery(query, maximumLimit())
      // Title-level holds in status "Open - Not yet filled" are not supposed to be linked to items,
      // but we need double-check anyway. These requests can be filtered out on DB level using
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
      .thenApply(r -> r.map(instances -> mapRequestsToInstances(requests, instances)));
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


  private MultipleRecords<Request> mapRequestsToInstances(MultipleRecords<Request> requests,
    MultipleRecords<Instance> instances) {

    Map<String, Instance> instancesById = instances.getRecordsMap(Instance::getId);
    return requests.mapRecords(r -> r.withInstance(instancesById.get(r.getInstanceId())));
  }

}
