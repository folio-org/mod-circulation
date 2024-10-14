package org.folio.circulation.resources;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria.byIndex;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.notice.TemplateContextUtil;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.HoldingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.InstanceRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.users.AddressTypeRepository;
import org.folio.circulation.infrastructure.storage.users.DepartmentRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.context.StaffSlipsContext;
import org.folio.circulation.storage.mappers.LocationMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public abstract class SlipsResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final PageLimit LOCATIONS_LIMIT = PageLimit.oneThousand();
  private static final String LOCATIONS_KEY = "locations";
  private static final String STATUS_KEY = "status";
  private static final String REQUESTS_KEY = "requests";
  private static final String ITEM_ID_KEY = "itemId";
  private static final String STATUS_NAME_KEY = "status.name";
  private static final String REQUEST_TYPE_KEY = "requestType";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private static final String SERVICE_POINT_ID_PARAM = "servicePointId";
  private static final String EFFECTIVE_LOCATION_ID_KEY = "effectiveLocationId";
  private static final String PRIMARY_SERVICE_POINT_KEY = "primaryServicePoint";

  private final String rootPath;
  private final String collectionName;
  private final RequestType requestType;
  private final Collection<ItemStatus> itemStatuses;

  protected SlipsResource(String rootPath, HttpClient client, String collectionName,
    RequestType requestType, ItemStatus itemStatus) {

    this(rootPath, client, collectionName, requestType, List.of(itemStatus));
  }

  protected SlipsResource(String rootPath, HttpClient client, String collectionName,
    RequestType requestType, Collection<ItemStatus> itemStatuses) {

    super(client);
    this.rootPath = rootPath;
    this.requestType = requestType;
    this.itemStatuses = itemStatuses;
    this.collectionName = collectionName;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);
    routeRegistration.getMany(this::getMany);
  }

  private void getMany(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final var userRepository = new UserRepository(clients);
    final var itemRepository = new ItemRepository(clients);
    final var addressTypeRepository = new AddressTypeRepository(clients);
    final var servicePointRepository = new ServicePointRepository(clients);
    final var patronGroupRepository = new PatronGroupRepository(clients);
    final var departmentRepository = new DepartmentRepository(clients);
    final UUID servicePointId = UUID.fromString(
      routingContext.request().getParam(SERVICE_POINT_ID_PARAM));

      fetchLocationsForServicePoint(servicePointId, clients)
        .thenComposeAsync(r -> r.after(ctx -> fetchItemsForLocations(ctx,
          itemRepository, LocationRepository.using(clients, servicePointRepository))))
        .thenComposeAsync(r -> r.after(ctx -> fetchRequests(ctx, clients)))
        .thenComposeAsync(r -> r.after(ctx -> userRepository.findUsersForRequests(
          ctx.getRequests())))
        .thenComposeAsync(result -> result.after(patronGroupRepository::findPatronGroupsForRequestsUsers))
        .thenComposeAsync(r -> r.after(departmentRepository::findDepartmentsForRequestUsers))
        .thenComposeAsync(r -> r.after(addressTypeRepository::findAddressTypesForRequests))
        .thenComposeAsync(r -> r.after(servicePointRepository::findServicePointsForRequests))
        .thenApply(flatMapResult(this::mapResultToJson))
        .thenComposeAsync(r -> r.combineAfter(() -> servicePointRepository.getServicePointById(servicePointId),
          this::addPrimaryServicePointNameToStaffSlipContext))
        .thenApply(r -> r.map(JsonHttpResponse::ok))
        .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchTitleLevelRequests(
    StaffSlipsContext staffSlipsContext, Clients clients) {

    final var instanceRepository = new InstanceRepository(clients);
    final var holdingsRepository = new HoldingsRepository(clients.holdingsStorage());

    return fetchTitleLevelRequests(clients, staffSlipsContext)
      .thenComposeAsync(r -> r.after(ctx -> fetchByInstancesByRequests(ctx, instanceRepository)))
      .thenApply(r -> r.next(this::mapRequestsToInstances))
      .thenComposeAsync(r -> r.after(ctx -> fetchHoldingsByInstances(ctx, holdingsRepository)))
      .thenApply(r -> r.next(this::mapRequestsToHoldings));
  }

  private Result<StaffSlipsContext> mapRequestsToInstances(StaffSlipsContext context) {
    log.debug("mapRequestsToInstances:: parameters context: {}", context);
    if (context.getInstances() == null || context.getInstances().isEmpty()) {
      log.info("mapRequestsToInstances:: no instances found");
      return succeeded(context);
    }

    Set<String> fetchedInstanceIds = context.getInstances().getRecords().stream()
      .map(Instance::getId)
      .collect(Collectors.toSet());

    Map<Request, String> requestToInstanceIdMap = context.getTlrRequests().getRecords().stream()
      .filter(request -> fetchedInstanceIds.contains(request.getInstanceId()))
      .collect(Collectors.toMap(identity(), Request::getInstanceId));

    return succeeded(context.withRequestToInstanceIdMap(requestToInstanceIdMap));
  }

  private Result<StaffSlipsContext> mapRequestsToHoldings(StaffSlipsContext context) {
    log.debug("mapRequestsToHoldings:: parameters context: {}", context);
    MultipleRecords<Holdings> holdings = context.getHoldings();
    if (holdings == null || holdings.isEmpty()) {
      log.info("mapRequestsToHoldings:: holdings are empty");
      return succeeded(context);
    }

    Map<String, Holdings> instanceIdToHoldingsMap = holdings.getRecords().stream()
      .collect(Collectors.toMap(Holdings::getInstanceId, identity(),
        (existing, replacement) -> existing));

    Map<Request, String> requestToInstanceIdMap = context.getRequestToInstanceIdMap();
    if (requestToInstanceIdMap == null || requestToInstanceIdMap.isEmpty()) {
      log.info("mapRequestsToHoldings:: no requests matched to holdings");
      return succeeded(context);
    }

    Map<Request, Holdings> requestToHoldingsMap = requestToInstanceIdMap.entrySet().stream()
      .filter(entry -> entry.getValue() != null && instanceIdToHoldingsMap.containsKey(
        entry.getValue()))
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> instanceIdToHoldingsMap.get(
        entry.getValue())));

    return succeeded(context.withRequestToHoldingMap(requestToHoldingsMap));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchByInstancesByRequests(
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

    if (ctx.getRequestToInstanceIdMap() == null || ctx.getRequestToInstanceIdMap().isEmpty()) {
      log.info("fetchHoldingsByInstances:: instances no requests matched to instances found");

      return ofAsync(ctx);
    }

    return holdingsRepository.fetchByInstances(ctx.getRequestToInstanceIdMap().values())
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

  private CompletableFuture<Result<StaffSlipsContext>> fetchLocationsForServicePoint(
    UUID servicePointId, Clients clients) {

    log.debug("fetchLocationsForServicePoint:: parameters servicePointId: {}", servicePointId);

    return findWithCqlQuery(clients.locationsStorage(), LOCATIONS_KEY, new LocationMapper()::toDomain)
      .findByQuery(exactMatch(PRIMARY_SERVICE_POINT_KEY, servicePointId.toString()), LOCATIONS_LIMIT)
      .thenApply(r -> r.map(locations -> new StaffSlipsContext().withLocations(locations)));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchItemsForLocations(
    StaffSlipsContext context, ItemRepository itemRepository,
    LocationRepository locationRepository) {

    log.debug("fetchPagedItemsForLocations:: multipleLocations: {}",
      () -> multipleRecordsAsString(context.getLocations()));
    Collection<Location> locations = context.getLocations().getRecords();
    Set<String> locationIds = locations.stream()
      .map(Location::getId)
      .filter(StringUtils::isNoneBlank)
      .collect(toSet());

    if (locationIds.isEmpty()) {
      log.info("fetchPagedItemsForLocations:: locationIds is empty");

      return ofAsync(context);
    }

    List<String> itemStatusValues = itemStatuses.stream()
      .map(ItemStatus::getValue)
      .toList();
    Result<CqlQuery> statusQuery = exactMatchAny(STATUS_NAME_KEY, itemStatusValues);

    return itemRepository.findByIndexNameAndQuery(locationIds, EFFECTIVE_LOCATION_ID_KEY, statusQuery)
      .thenComposeAsync(r -> r.after(items -> fetchLocationDetailsForItems(items, locations,
        locationRepository)))
      .thenApply(r -> r.map(context::withItems));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchRequests(
    StaffSlipsContext context, Clients clients) {

    return fetchItemLevelRequests(context, clients)
      .thenComposeAsync(r -> r.after(ctx -> fetchTitleLevelRequests(ctx, clients)))
      .thenApply(r -> r.next(this::combineRequests));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchItemLevelRequests(
    StaffSlipsContext context, Clients clients) {

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

    final Result<CqlQuery> typeQuery = exactMatch(REQUEST_TYPE_KEY, requestType.getValue());
    final Result<CqlQuery> statusQuery = exactMatch(STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue());
    final Result<CqlQuery> statusAndTypeQuery = typeQuery.combine(statusQuery, CqlQuery::and);

    return findWithMultipleCqlIndexValues(clients.requestsStorage(), REQUESTS_KEY, Request::from)
      .find(byIndex(ITEM_ID_KEY, itemIds).withQuery(statusAndTypeQuery))
      .thenApply(flatMapResult(requests -> matchItemsToRequests(requests, items)))
      .thenApply(r -> r.map(context::withRequests));
  }

  private CompletableFuture<Result<StaffSlipsContext>> fetchTitleLevelRequests(
    Clients clients, StaffSlipsContext context) {

    final Result<CqlQuery> typeQuery = exactMatch(REQUEST_TYPE_KEY, requestType.getValue());
    final Result<CqlQuery> statusQuery = exactMatch(STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue());
    final Result<CqlQuery> statusAndTypeQuery = typeQuery.combine(statusQuery, CqlQuery::and);

    return findWithCqlQuery(
      clients.requestsStorage(), REQUESTS_KEY, Request::from)
      .findByQuery(statusAndTypeQuery)
      .thenApply(r -> r.next(this::filterRequests))
      .thenApply(r -> r.map(context::withTlrRequests));
  }

  private Result<MultipleRecords<Request>> filterRequests(MultipleRecords<Request> requests) {
    var filteredRequests = requests.getRecords().stream()
      .filter(request -> request.getItemId() == null)
      .toList();

    return succeeded(new MultipleRecords<>(filteredRequests, filteredRequests.size()));
  }

  private CompletableFuture<Result<Collection<Item>>> fetchLocationDetailsForItems(
    MultipleRecords<Item> items, Collection<Location> locationsForServicePoint,
    LocationRepository locationRepository) {

    log.debug("fetchLocationDetailsForItems:: parameters items: {}",
      () -> multipleRecordsAsString(items));

    Set<String> locationIdsFromItems = items.toKeys(Item::getEffectiveLocationId);
    Set<Location> locationsForItems = locationsForServicePoint.stream()
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
      .thenApply(flatMapResult(locations -> matchLocationsToItems(items, locations)));
  }

  private Result<Collection<Item>> matchLocationsToItems(
    MultipleRecords<Item> items, Collection<Location> locations) {

    log.debug("matchLocationsToItems:: parameters items: {}, locations: {}",
      () -> multipleRecordsAsString(items), () -> collectionAsString(locations));

    Map<String, Location> locationsMap = locations.stream()
      .collect(toMap(Location::getId, identity()));

    return succeeded(items.mapRecords(item -> item.withLocation(
        locationsMap.getOrDefault(item.getEffectiveLocationId(),
          Location.unknown(item.getEffectiveLocationId()))))
      .getRecords());
  }

  private Result<MultipleRecords<Request>> matchItemsToRequests(
    MultipleRecords<Request> requests, Collection<Item> items) {

    Map<String, Item> itemMap = items.stream()
      .collect(toMap(Item::getItemId, identity()));

    return succeeded(requests.mapRecords(request -> request.withItem(
      itemMap.getOrDefault(request.getItemId(), null))));
  }

  private Result<JsonObject> mapResultToJson(MultipleRecords<Request> requests) {
    log.debug("mapResultToJson:: parameters requests: {}", () -> multipleRecordsAsString(requests));
    List<JsonObject> representations = requests.getRecords().stream()
      .map(TemplateContextUtil::createStaffSlipContext)
      .toList();
    JsonObject jsonRepresentations = new JsonObject()
      .put(collectionName, representations)
      .put(TOTAL_RECORDS_KEY, representations.size());

    return succeeded(jsonRepresentations);
  }

  private JsonObject addPrimaryServicePointNameToStaffSlipContext(JsonObject context,
    ServicePoint servicePoint) {

    return TemplateContextUtil.addPrimaryServicePointNameToStaffSlipContext(
      context, servicePoint, collectionName);
  }
}
