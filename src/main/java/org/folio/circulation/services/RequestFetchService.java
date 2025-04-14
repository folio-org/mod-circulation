package org.folio.circulation.services;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria.byIndex;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.PageLimit.maximumLimit;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.mapAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

public class RequestFetchService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String ITEM_ID_KEY = "itemId";
  private static final String STATUS_KEY = "status";
  private static final String REQUESTS_KEY = "requests";
  private static final String REQUEST_TYPE_KEY = "requestType";
  private static final String REQUEST_LEVEL_KEY = "requestLevel";

  public CompletableFuture<Result<StaffSlipsContext>> fetchRequests(
    StaffSlipsContext context, Clients clients, RequestType requestType) {

    return fetchItemLevelRequests(context, clients, requestType)
      .thenComposeAsync(r -> r.after(ctx -> fetchTitleLevelRequests(ctx, clients, requestType)))
      .thenApply(r -> r.next(this::combineRequests));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchItemLevelRequests(
    StaffSlipsContext context, Clients clients, RequestType requestType) {

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

    var typeQuery = exactMatch(REQUEST_TYPE_KEY, requestType.getValue());
    var statusQuery = exactMatch(STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue());
    var requestLevelQuery = exactMatch(REQUEST_LEVEL_KEY, RequestLevel.ITEM.getValue());
    var statusAndTypeQuery = requestType.equals(RequestType.PAGE)
      ? typeQuery.combine(statusQuery, CqlQuery::and)
        .combine(requestLevelQuery, CqlQuery::and)
      : typeQuery.combine(statusQuery, CqlQuery::and);

    return findWithMultipleCqlIndexValues(clients.requestsStorage(), REQUESTS_KEY, Request::from)
      .find(byIndex(ITEM_ID_KEY, itemIds).withQuery(statusAndTypeQuery))
      .thenApply(flatMapResult(requests -> matchItemsToRequests(requests, items)))
      .thenApply(r -> r.map(context::withRequests));
  }

  private Result<MultipleRecords<Request>> matchItemsToRequests(
    MultipleRecords<Request> requests, Collection<Item> items) {

    Map<String, Item> itemMap = items.stream()
      .collect(toMap(Item::getItemId, identity()));

    return succeeded(requests.mapRecords(request -> request.withItem(
      itemMap.getOrDefault(request.getItemId(), null))));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchTitleLevelRequests(
    StaffSlipsContext staffSlipsContext, Clients clients, RequestType requestType) {

    var instanceRepository = new InstanceRepository(clients);
    var holdingsRepository = new HoldingsRepository(clients.holdingsStorage());

    return fetchTitleLevelRequests(clients, staffSlipsContext, requestType)
      .thenComposeAsync(r -> r.after(ctx -> fetchInstancesByRequests(ctx, instanceRepository)))
      .thenApply(r -> r.next(this::mapRequestsToInstances))
      .thenComposeAsync(r -> r.after(ctx -> fetchHoldingsByInstances(ctx, holdingsRepository)))
      .thenApply(r -> r.next(this::mapRequestsToHoldings));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchTitleLevelRequests(
    Clients clients, StaffSlipsContext context, RequestType requestType) {

    var typeQuery = exactMatch(REQUEST_TYPE_KEY, requestType.getValue());
    var statusQuery = exactMatch(STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue());
    var requestLevelQuery = exactMatch(REQUEST_LEVEL_KEY, RequestLevel.TITLE.getValue());
    var statusTypeAndLevelQuery = typeQuery.combine(statusQuery, CqlQuery::and)
      .combine(requestLevelQuery, CqlQuery::and);

    return findWithCqlQuery(clients.requestsStorage(), REQUESTS_KEY, Request::from)
      .findByQuery(statusTypeAndLevelQuery, maximumLimit())
      .thenApply(flatMapResult(requests -> matchItemsToRequests(requests, context.getItems())))
      .thenApply(r -> r.map(context::withTlrRequests));
  }

  private Result<StaffSlipsContext> mapRequestsToInstances(StaffSlipsContext context) {
    log.debug("mapRequestsToInstances:: parameters context: {}", context);
    if (context.getInstances() == null || context.getInstances().isEmpty()) {
      log.info("mapRequestsToInstances:: no instances found");
      return succeeded(context);
    }

    var fetchedInstancesByIdMap = context.getInstances().getRecords().stream()
      .collect(Collectors.toMap(Instance::getId, identity()));
    log.info("mapRequestsToInstances:: fetchedInstanceIds: {}",
      () -> collectionAsString(fetchedInstancesByIdMap.keySet()));

    var requestToInstanceMap = context.getTlrRequests().getRecords().stream()
      .filter(request -> fetchedInstancesByIdMap.containsKey(request.getInstanceId()))
      .collect(Collectors.toMap(
        r -> r.withInstance(fetchedInstancesByIdMap.get(r.getInstanceId())),
        r -> fetchedInstancesByIdMap.get(r.getInstanceId()))
      );
    log.info("mapRequestsToInstances:: requestToInstanceIdMap: {}",
      () -> mapAsString(requestToInstanceMap));

    return succeeded(context.withRequestToInstanceMap(requestToInstanceMap));
  }

  private Result<StaffSlipsContext> mapRequestsToHoldings(StaffSlipsContext context) {
    log.debug("mapRequestsToHoldings:: parameters context: {}", context);
    MultipleRecords<Holdings> holdings = context.getHoldings();
    if (holdings == null || holdings.isEmpty()) {
      log.info("mapRequestsToHoldings:: holdings are empty");
      return succeeded(context);
    }

    var holdingsToInstanceIdMap = holdings.getRecords().stream()
      .collect(Collectors.toMap(identity(), Holdings::getInstanceId));

    var requestToInstanceMap = context.getRequestToInstanceMap();
    if (requestToInstanceMap == null || requestToInstanceMap.isEmpty()) {
      log.info("mapRequestsToHoldings:: no requests matched to holdings");
      return succeeded(context);
    }

    var requestToHoldingsMap = requestToInstanceMap.entrySet().stream()
      .filter(entry -> entry.getValue() != null && holdingsToInstanceIdMap.containsValue(
        entry.getValue().getId()))
      .collect(Collectors.toMap(Map.Entry::getKey,
        entry -> findHoldingsByInstanceId(holdings, entry.getValue().getId())));
    log.info("mapRequestsToHoldings:: requestToHoldingsMap: {}",
      () -> mapAsString(requestToHoldingsMap));

    return succeeded(context.withRequestToHoldingMap(requestToHoldingsMap));
  }

  private Holdings findHoldingsByInstanceId(MultipleRecords<Holdings> holdings,
    String instanceId) {

    return holdings.getRecords().stream()
      .filter(holding -> holding.getInstanceId().equals(instanceId))
      .findFirst()
      .orElse(null);
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchInstancesByRequests(
    StaffSlipsContext ctx, InstanceRepository instanceRepository) {

    var tlrRequests = ctx.getTlrRequests();
    if (tlrRequests == null || tlrRequests.isEmpty()) {
      log.info("fetchByInstancesByRequests:: no TLR requests found");

      return ofAsync(ctx);
    }

    return instanceRepository.fetchByRequests(ctx.getTlrRequests())
      .thenApply(r -> r.map(ctx::withInstances));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchHoldingsByInstances(
    StaffSlipsContext ctx, HoldingsRepository holdingsRepository) {

    if (ctx.getRequestToInstanceMap() == null || ctx.getRequestToInstanceMap().isEmpty()) {
      log.info("fetchHoldingsByInstances:: instances no requests matched to instances found");

      return ofAsync(ctx);
    }

    var instanceIds = ctx.getRequestToInstanceMap().values().stream()
      .map(Instance::getId)
      .toList();
    return holdingsRepository.fetchByInstances(instanceIds)
      .thenApply(r -> r.map(ctx::withHoldings));
  }

  private Result<StaffSlipsContext> combineRequests(StaffSlipsContext ctx) {
    log.debug("combineRequests:: parameters ctx: {}", ctx);
    Map<Request, Holdings> requestToHoldingMap = ctx.getRequestToHoldingMap();
    if (requestToHoldingMap == null || requestToHoldingMap.isEmpty()) {
      log.info("combineRequests:: no tlr requests to combine");
      return succeeded(ctx);
    }

    Set<String> locationIds = ctx.getLocations().getRecords().stream()
      .map(Location::getId)
      .collect(Collectors.toSet());

    List<Request> requestsToAdd = requestToHoldingMap.entrySet().stream()
      .filter(entry -> locationIds.contains(entry.getValue().getEffectiveLocationId()))
      .map(Map.Entry::getKey)
      .toList();

    List<Request> updatedRequests = new ArrayList<>(ctx.getRequests().getRecords());
    updatedRequests.addAll(requestsToAdd);

    return succeeded(ctx.withRequests(new MultipleRecords<>(updatedRequests,
      updatedRequests.size())));
  }
}
