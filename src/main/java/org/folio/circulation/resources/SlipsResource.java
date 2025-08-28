package org.folio.circulation.resources;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CirculationSetting;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.mapper.StaffSlipMapper;
import org.folio.circulation.infrastructure.storage.CirculationSettingsRepository;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.users.AddressTypeRepository;
import org.folio.circulation.infrastructure.storage.users.DepartmentRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.context.StaffSlipsContext;
import org.folio.circulation.services.RequestFetchService;
import org.folio.circulation.storage.mappers.LocationMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public abstract class SlipsResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final PageLimit LOCATIONS_LIMIT = PageLimit.oneThousand();
  private static final String LOCATIONS_KEY = "locations";
  private static final String STATUS_NAME_KEY = "status.name";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private static final String SEARCH_SLIPS_KEY = "searchSlips";
  private static final String PICK_SLIPS_KEY = "pickSlips";
  private static final String SERVICE_POINT_ID_PARAM = "servicePointId";
  private static final String EFFECTIVE_LOCATION_ID_KEY = "effectiveLocationId";
  private static final String PRIMARY_SERVICE_POINT_KEY = "primaryServicePoint";
  private static final String PRINT_EVENT_FLAG_QUERY = "query=name=printEventLogFeature";
  public static final String PRINT_EVENT_LOG_FEATURE = "printEventLogFeature";

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

    final UUID servicePointId = UUID.fromString(
      routingContext.request().getParam(SERVICE_POINT_ID_PARAM));
    log.info("getMany:: servicePointId: {}", servicePointId);

    isStaffSlipsPrintingDisabled(clients)
      .thenCompose(r -> r.after(isPrintingDisabled -> buildStaffSlips(
        servicePointId, clients, isPrintingDisabled)))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<JsonObject>> buildStaffSlips(UUID servicePointId,
    Clients clients, boolean isPrintingDisabled) {

    if (isPrintingDisabled) {
      return ofAsync(new JsonObject()
        .put(collectionName, new JsonArray())
        .put(TOTAL_RECORDS_KEY, 0));
    }

    final var userRepository = new UserRepository(clients);
    final var itemRepository = new ItemRepository(clients);
    final var addressTypeRepository = new AddressTypeRepository(clients);
    final var servicePointRepository = new ServicePointRepository(clients);
    final var patronGroupRepository = new PatronGroupRepository(clients);
    final var departmentRepository = new DepartmentRepository(clients);

    return fetchLocationsForServicePoint(servicePointId, clients)
      .thenComposeAsync(r -> r.after(ctx -> fetchItemsForLocations(ctx,
        itemRepository, LocationRepository.using(clients, servicePointRepository))))
      .thenComposeAsync(r -> r.after(ctx -> new RequestFetchService().fetchRequests(ctx, clients, requestType)))
      .thenComposeAsync(r -> r.after(ctx -> userRepository.findUsersForRequests(
        ctx.getRequests())))
      .thenComposeAsync(r -> r.after(patronGroupRepository::findPatronGroupsForRequestsUsers))
      .thenComposeAsync(r -> r.after(departmentRepository::findDepartmentsForRequestUsers))
      .thenComposeAsync(r -> r.after(addressTypeRepository::findAddressTypesForRequests))
      .thenComposeAsync(r -> r.after(servicePointRepository::findServicePointsForRequests))
      .thenApply(flatMapResult(this::mapResultToJson))
      .thenComposeAsync(r -> r.combineAfter(() -> servicePointRepository.getServicePointById(servicePointId),
        this::addPrimaryServicePointNameToStaffSlipContext));
  }

  private CompletableFuture<Result<Boolean>> isStaffSlipsPrintingDisabled(Clients clients) {
    if (PICK_SLIPS_KEY.equals(collectionName) && requestType == RequestType.PAGE) {
      log.info("isStaffSlipsPrintingDisabled:: PICK_SLIPS_KEY and PAGE requestType condition met");
      return new CirculationSettingsRepository(clients).findBy(PRINT_EVENT_FLAG_QUERY)
        .thenApply(r -> r.map(this::isPrintingPickSlipsDisabled));
    } else if (SEARCH_SLIPS_KEY.equals(collectionName) && requestType == RequestType.HOLD) {
      log.info("isStaffSlipsPrintingDisabled:: SEARCH_SLIPS_KEY and HOLD requestType condition met");
      return new ConfigurationRepository(clients).lookupPrintHoldRequestsEnabled()
        .thenApply(r -> r.map(config -> !config.isPrintHoldRequestsEnabled()));
    } else {
      return ofAsync(false);
    }
  }

  private boolean isPrintingPickSlipsDisabled(
    MultipleRecords<CirculationSetting> settingsRecords) {

    log.debug("isPrintingPickSlipsEnabled:: parameters settingsRecords: {}",
      () -> multipleRecordsAsString(settingsRecords));

    if (settingsRecords == null) {
      log.info("isPrintingPickSlipsEnabled:: settingsRecords is null");
      return false;
    }

    return settingsRecords.getRecords()
      .stream()
      .filter(setting -> PRINT_EVENT_LOG_FEATURE.equals(setting.getName()))
      .findFirst()
      .map(CirculationSetting::getValue)
      .map(setting -> setting.getValue("enablePrintLog"))
      .map(enablePrintLogValue -> {
        if (enablePrintLogValue instanceof Boolean enablePrintLogBoolean) {
          return !enablePrintLogBoolean;
        } else if (enablePrintLogValue instanceof String enablePrintLogString) {
          return !BooleanUtils.toBoolean(enablePrintLogString);
        }
        return false;
      })
      .orElse(false);
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
      return ofAsync(context.withItems(emptyList()));
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
      .collect(toMap(Location::getId, identity(), (a, b) -> a));

    return succeeded(items.mapRecords(item -> item.withLocation(
        locationsMap.getOrDefault(item.getEffectiveLocationId(),
          Location.unknown(item.getEffectiveLocationId()))))
      .getRecords());
  }

  private Result<JsonObject> mapResultToJson(MultipleRecords<Request> requests) {
    log.debug("mapResultToJson:: parameters requests: {}", () -> multipleRecordsAsString(requests));
    List<JsonObject> representations = requests.getRecords().stream()
      .map(StaffSlipMapper::createStaffSlipContext)
      .toList();
    JsonObject jsonRepresentations = new JsonObject()
      .put(collectionName, representations)
      .put(TOTAL_RECORDS_KEY, representations.size());

    return succeeded(jsonRepresentations);
  }

  private JsonObject addPrimaryServicePointNameToStaffSlipContext(JsonObject context,
    ServicePoint servicePoint) {

    return StaffSlipMapper.addPrimaryServicePointNameToStaffSlipContext(
      context, servicePoint, collectionName);
  }
}
