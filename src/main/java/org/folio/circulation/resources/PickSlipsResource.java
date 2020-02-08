package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.LocationRepository;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.notice.TemplateContextUtil;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.flatMapResult;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;

public class PickSlipsResource extends Resource {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String STATUS_KEY = "status";
  private static final String ITEM_ID_KEY = "itemId";
  private static final String REQUESTS_KEY = "requests";
  private static final String LOCATIONS_KEY = "locations";
  private static final String PICK_SLIPS_KEY = "pickSlips";
  private static final String STATUS_NAME_KEY = "status.name";
  private static final String REQUEST_TYPE_KEY = "requestType";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
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
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    final UserRepository userRepository = new UserRepository(clients);

    UUID servicePointId = UUID.fromString(routingContext.request().getParam(SERVICE_POINT_ID_PARAM));

    fetchLocationsForServicePoint(servicePointId, clients)
      .thenComposeAsync(r -> r.after(locations -> fetchPagedItemsForLocations(locations, clients)))
      .thenComposeAsync(r -> r.after(items -> fetchOpenPageRequestsForItems(items, clients)))
      .thenComposeAsync(r -> r.after(userRepository::findUsersForRequests))
      .thenApply(flatMapResult(this::mapResultToJson))
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<MultipleRecords<Location>>> fetchLocationsForServicePoint(
    UUID servicePointId, Clients clients) {

    return new MultipleRecordFetcher<>(clients.locationsStorage(), LOCATIONS_KEY, Location::from)
      .findByQuery(exactMatch(PRIMARY_SERVICE_POINT_KEY, servicePointId.toString()));
  }

  private CompletableFuture<Result<Collection<Item>>> fetchPagedItemsForLocations(
    MultipleRecords<Location> multipleLocations, Clients clients) {

    Collection<Location> locations = multipleLocations.getRecords();

    Set<String> locationIds = locations.stream()
      .map(Location::getId)
      .filter(StringUtils::isNoneBlank)
      .collect(toSet());

    if (locationIds.isEmpty()) {
      log.info("No locations to search paged items for");
      return completedFuture(succeeded(Collections.emptyList()));
    }

    // we already have locations, so no need to fetch them again
    final ItemRepository itemRepository = new ItemRepository(clients, false, true, true);
    Result<CqlQuery> statusQuery = exactMatch(STATUS_NAME_KEY, ItemStatus.PAGED.getValue());
    
    return itemRepository.findByIndexNameAndQuery(locationIds, EFFECTIVE_LOCATION_ID_KEY, statusQuery)
      .thenComposeAsync(r -> r.after(items -> fetchLocationDetailsForItems(items, locations, clients)));
  }

  private CompletableFuture<Result<Collection<Item>>> fetchLocationDetailsForItems(
    Collection<Item> items, Collection<Location> locationsForServicePoint, Clients clients) {

    Set<String> locationIdsFromItems = items.stream()
      .map(Item::getLocationId)
      .filter(StringUtils::isNoneBlank)
      .collect(toSet());

    Set<Location> locationsForItems = locationsForServicePoint.stream()
      .filter(location -> locationIdsFromItems.contains(location.getId()))
      .collect(toSet());

    if (locationsForItems.isEmpty()) {
      log.info("No locations to load details for");
      return completedFuture(succeeded(Collections.emptyList()));
    }

    LocationRepository locationRepository = LocationRepository.using(clients);

    return completedFuture(succeeded(locationsForItems))
      .thenComposeAsync(r -> r.after(locationRepository::loadLibraries))
      .thenComposeAsync(r -> r.after(locationRepository::loadInstitutions))
      .thenComposeAsync(r -> r.after(locationRepository::loadCampuses))
      .thenApply(flatMapResult(locations -> matchLocationsToItems(items, locations)));
  }

  private Result<Collection<Item>> matchLocationsToItems(Collection<Item> items, Collection<Location> locations) {
    Map<String, Location> locationsMap = locations.stream()
      .collect(toMap(Location::getId, identity()));

    return succeeded(
      items.stream()
        .map(item -> item.withLocation(locationsMap.getOrDefault(item.getLocationId(), null)))
        .collect(toSet())
    );
  }

  private CompletableFuture<Result<MultipleRecords<Request>>> fetchOpenPageRequestsForItems(
    Collection<Item> items, Clients clients) {

    Set<String> itemIds = items.stream()
      .map(Item::getItemId)
      .filter(StringUtils::isNoneBlank)
      .collect(toSet());
    
    if(itemIds.isEmpty()) {
      log.info("No items to search requests for");
      return completedFuture(succeeded(MultipleRecords.empty()));
    }

    final Result<CqlQuery> typeQuery = exactMatch(REQUEST_TYPE_KEY, RequestType.PAGE.getValue());
    final Result<CqlQuery> statusQuery = exactMatch(STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue());
    final Result<CqlQuery> statusAndTypeQuery = typeQuery.combine(statusQuery, CqlQuery::and);

    return new MultipleRecordFetcher<>(clients.requestsStorage(), REQUESTS_KEY, Request::from)
      .findByIndexNameAndQuery(itemIds, ITEM_ID_KEY, statusAndTypeQuery)
      .thenApply(flatMapResult(requests -> matchItemsToRequests(requests, items)));
  }

  private Result<MultipleRecords<Request>> matchItemsToRequests(
    MultipleRecords<Request> requests, Collection<Item> items) {

    Map<String, Item> itemMap = items.stream()
      .collect(toMap(Item::getItemId, identity()));

    return succeeded(
      requests.mapRecords(request ->
        request.withItem(itemMap.getOrDefault(request.getItemId(), null))
    ));
  }

  private Result<JsonObject> mapResultToJson(MultipleRecords<Request> requests) {
    List<JsonObject> representations = requests.getRecords().stream()
      .map(TemplateContextUtil::createStaffSlipContext)
      .collect(Collectors.toList());

    JsonObject jsonRepresentations = new JsonObject()
      .put(PICK_SLIPS_KEY, representations)
      .put(TOTAL_RECORDS_KEY, representations.size());

    return succeeded(jsonRepresentations);
  }

}
