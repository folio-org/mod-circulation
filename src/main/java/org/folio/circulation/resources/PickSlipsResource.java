package org.folio.circulation.resources;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria.byIndex;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.notice.TemplateContextUtil;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.users.AddressTypeRepository;
import org.folio.circulation.infrastructure.storage.users.DepartmentRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class PickSlipsResource extends SlipsResource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String PICK_SLIPS_KEY = "pickSlips";
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

  protected void getMany(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final var userRepository = new UserRepository(clients);
    final var itemRepository = new ItemRepository(clients);
    final AddressTypeRepository addressTypeRepository = new AddressTypeRepository(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final PatronGroupRepository patronGroupRepository = new PatronGroupRepository(clients);
    final DepartmentRepository departmentRepository = new DepartmentRepository(clients);
    final UUID servicePointId = UUID.fromString(
      routingContext.request().getParam(SERVICE_POINT_ID_PARAM));

    fetchLocationsForServicePoint(servicePointId, clients)
      .thenComposeAsync(r -> r.after(locations -> fetchPagedItemsForLocations(locations,
        itemRepository, LocationRepository.using(clients, servicePointRepository))))
      .thenComposeAsync(r -> r.after(items -> fetchOpenPageRequestsForItems(items, clients)))
      .thenComposeAsync(r -> r.after(userRepository::findUsersForRequests))
      .thenComposeAsync(result -> result.after(patronGroupRepository::findPatronGroupsForRequestsUsers))
      .thenComposeAsync(r -> r.after(departmentRepository::findDepartmentsForRequestUsers))
      .thenComposeAsync(r -> r.after(addressTypeRepository::findAddressTypesForRequests))
      .thenComposeAsync(r -> r.after(servicePointRepository::findServicePointsForRequests))
      .thenApply(flatMapResult(requests -> mapResultToJson(requests, PICK_SLIPS_KEY)))
      .thenComposeAsync(r -> r.combineAfter(() -> servicePointRepository.getServicePointById(servicePointId),
        TemplateContextUtil::addPrimaryServicePointNameToStaffSlipContext))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<Collection<Item>>> fetchPagedItemsForLocations(
    MultipleRecords<Location> multipleLocations,
    ItemRepository itemRepository, LocationRepository locationRepository) {

    log.debug("fetchPagedItemsForLocations:: parameters multipleLocations: {}",
      () -> multipleRecordsAsString(multipleLocations));
    Collection<Location> locations = multipleLocations.getRecords();

    Set<String> locationIds = locations.stream()
      .map(Location::getId)
      .filter(StringUtils::isNoneBlank)
      .collect(toSet());

    if (locationIds.isEmpty()) {
      log.info("fetchPagedItemsForLocations:: locationIds is empty");

      return completedFuture(succeeded(emptyList()));
    }

    Result<CqlQuery> statusQuery = exactMatch(STATUS_NAME_KEY, ItemStatus.PAGED.getValue());

    return itemRepository.findByIndexNameAndQuery(locationIds, EFFECTIVE_LOCATION_ID_KEY, statusQuery)
      .thenComposeAsync(r -> r.after(items -> fetchLocationDetailsForItems(items, locations,
        locationRepository)));
  }

  private CompletableFuture<Result<MultipleRecords<Request>>> fetchOpenPageRequestsForItems(
    Collection<Item> items, Clients clients) {

    Set<String> itemIds = items.stream()
      .map(Item::getItemId)
      .filter(StringUtils::isNoneBlank)
      .collect(toSet());

    if (itemIds.isEmpty()) {
      log.info("fetchOpenPageRequestsForItems:: itemIds is empty");

      return completedFuture(succeeded(MultipleRecords.empty()));
    }

    final Result<CqlQuery> typeQuery = exactMatch(REQUEST_TYPE_KEY, RequestType.PAGE.getValue());
    final Result<CqlQuery> statusQuery = exactMatch(STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue());
    final Result<CqlQuery> statusAndTypeQuery = typeQuery.combine(statusQuery, CqlQuery::and);

    return findWithMultipleCqlIndexValues(clients.requestsStorage(), REQUESTS_KEY, Request::from)
      .find(byIndex(ITEM_ID_KEY, itemIds).withQuery(statusAndTypeQuery))
      .thenApply(flatMapResult(requests -> matchItemsToRequests(requests, items)));
  }
}
