package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.representations.ItemPickSlipRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;

public class PickSlipsResource extends Resource {
  private static final String ID_KEY = "id";
  private static final String ITEMS_KEY = "items";
  private static final String STATUS_KEY = "status";
  private static final String ITEM_ID_KEY = "itemId";
  private static final String REQUESTS_KEY = "requests";
  private static final String LOCATIONS_KEY = "locations";
  private static final String INSTANCES_KEY = "instances";
  private static final String PICK_SLIPS_KEY = "pickSlips";
  private static final String STATUS_NAME_KEY = "status.name";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private static final String HOLDINGS_RECORDS_KEY = "holdingsRecords";
  private static final String SERVICE_POINT_ID_PARAM = "servicePointId";
  private static final String EFFECTIVE_LOCATION_ID_KEY = "effectiveLocationId";
  private static final String PRIMARY_SERVICE_POINT_KEY = "primaryServicePoint";

  private final String rootPath;

  public PickSlipsResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);
    routeRegistration.getMany(this::getMany);
  }

  private void getMany(RoutingContext routingContext) {
    final UUID servicePointId = UUID.fromString(routingContext.request().getParam(SERVICE_POINT_ID_PARAM));

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    fetchLocationsForServicePoint(servicePointId, clients.locationsStorage())
      .thenComposeAsync(r -> r.after(locations -> fetchPagedItemsByLocations(locations, clients.itemsStorage())))
      .thenComposeAsync(r -> r.after(items -> filterItemsByOpenUnfilledRequests(items, clients.requestsStorage())))
      .thenComposeAsync(r -> r.after(items -> fetchHoldings(items, clients.holdingsStorage())))
      .thenComposeAsync(r -> r.after(items -> fetchInstances(items, clients.instancesStorage())))
      .thenApply(r -> r.next(this::mapResultToJson))
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<Set<Location>>> fetchLocationsForServicePoint(
    UUID servicePointId, CollectionResourceClient locationsStorageClient) {

    return new MultipleRecordFetcher<>(locationsStorageClient, LOCATIONS_KEY, Location::from)
      .findByQuery(exactMatch(PRIMARY_SERVICE_POINT_KEY, servicePointId.toString()))
      .thenApply(r -> r.next(this::recordsToSet));
  }

  private CompletableFuture<Result<Set<Item>>> fetchPagedItemsByLocations(
    Set<Location> locations, CollectionResourceClient itemStorageClient) {

    Set<String> locationIds = locations.stream()
      .map(Location::getId)
      .collect(Collectors.toSet());

    final Result<CqlQuery> statusQuery = exactMatch(STATUS_NAME_KEY, ItemStatus.PAGED.getValue());

    return new MultipleRecordFetcher<>(itemStorageClient, ITEMS_KEY, Item::from)
      .findByIdIndexAndQuery(locationIds, EFFECTIVE_LOCATION_ID_KEY, statusQuery)
      .thenApply(r -> r.next(this::recordsToSet))
      .thenApply(r -> r.next(items -> populateLocationsInItems(items, locations)));
  }

  private Result<Set<Item>> populateLocationsInItems(Set<Item> items, Set<Location> locations) {
    Map<String, Location> locationMap = locations.stream()
        .collect(Collectors.toMap(Location::getId, identity()));

    return Result.succeeded(
      items.stream()
        .map(item -> item.withLocation(locationMap.get(item.getLocationId())))
        .collect(Collectors.toSet()));
  }

  private CompletableFuture<Result<Set<Item>>> filterItemsByOpenUnfilledRequests(
      Set<Item> items, CollectionResourceClient client) {

    Set<String> itemIds = items.stream()
        .map(Item::getItemId)
        .collect(Collectors.toSet());

    final Result<CqlQuery> statusQuery = exactMatch(STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue());

    return new MultipleRecordFetcher<>(client, REQUESTS_KEY, Request::from)
      .findByIdIndexAndQuery(itemIds, ITEM_ID_KEY, statusQuery)
      .thenApply(r -> r.next(this::recordsToSet))
      .thenApply(r -> r.next(requests -> filterItemsByRequests(items, requests)));
  }

  private Result<Set<Item>> filterItemsByRequests(Set<Item> items, Set<Request> requests) {
    Set<String> requestedItemIds = requests.stream()
        .map(Request::getItemId)
        .collect(Collectors.toSet());

    return Result.succeeded(items.stream()
      .filter(i -> requestedItemIds.contains(i.getItemId()))
      .collect(Collectors.toSet()));
  }

  private CompletableFuture<Result<Set<Item>>> fetchHoldings(
    Set<Item> items, CollectionResourceClient client) {

      Set<String> holdingsIds = items.stream()
        .map(Item::getHoldingsRecordId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

      final MultipleRecordFetcher<JsonObject> fetcher
        = new MultipleRecordFetcher<>(client, HOLDINGS_RECORDS_KEY, identity());

      return fetcher.findByIds(holdingsIds)
        .thenApply(r -> r.map(holdings -> items.stream()
          .map(item -> item.withHoldingsRecord(
            findById(item.getHoldingsRecordId(), holdings.getRecords()).orElse(null)))
          .collect(Collectors.toSet())));
  }

  private CompletableFuture<Result<Set<Item>>> fetchInstances(
    Set<Item> items, CollectionResourceClient client) {

      Set<String> instanceIds = items.stream()
        .map(Item::getInstanceId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

      final MultipleRecordFetcher<JsonObject> fetcher
        = new MultipleRecordFetcher<>(client, INSTANCES_KEY, identity());

      return fetcher.findByIds(instanceIds)
        .thenApply(r -> r.map(instances -> items.stream()
          .map(item -> item.withInstance(
            findById(item.getInstanceId(), instances.getRecords()).orElse(null)))
          .collect(Collectors.toSet())));
  }

  private Result<JsonObject> mapResultToJson(Set<Item> items) {
    List<JsonObject> jsonItems = items.stream()
        .map(item -> new ItemPickSlipRepresentation().create(item))
        .collect(Collectors.toList());

    JsonObject jsonResult = new JsonObject()
        .put(PICK_SLIPS_KEY, jsonItems)
        .put(TOTAL_RECORDS_KEY, jsonItems.size());

    return Result.succeeded(jsonResult);
  }

  private <T> Result<Set<T>> recordsToSet(MultipleRecords<T> records) {
    return Result.succeeded(
      new HashSet<>(records.toKeys(identity())));
  }

  private Optional<JsonObject> findById(String id, Collection<JsonObject> collection) {
    return collection.stream()
      .filter(item -> item.getString(ID_KEY).equals(id))
      .findFirst();
  }

}
