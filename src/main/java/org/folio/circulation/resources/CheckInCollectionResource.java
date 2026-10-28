package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.folio.circulation.infrastructure.storage.CheckInStorageRepository.RECORDS_PROPERTY_NAME;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.representations.CheckInRecordRepresentation;
import org.folio.circulation.infrastructure.storage.CheckInStorageRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CheckInCollectionResource extends Resource {
  private static final String ROOT_PATH = "/circulation/check-ins";
  private static final String PERFORMED_BY_USER_ID = "performedByUserId";
  private static final String SERVICE_POINT_ID = "servicePointId";

  public CheckInCollectionResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(ROOT_PATH, router);

    routeRegistration.get(this::get);
    routeRegistration.getMany(this::getMany);
  }

  private void get(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var checkInStorageRepository = new CheckInStorageRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var servicePointRepository = new ServicePointRepository(clients);
    final var representation = new CheckInRecordRepresentation();

    final var id = routingContext.request().getParam("id");

    checkInStorageRepository.getById(id)
      .thenCompose(r -> r.combineAfter(
        checkIn -> userRepository.getUser(getProperty(checkIn, PERFORMED_BY_USER_ID)),
        representation::withSource))
      .thenCompose(r -> r.combineAfter(
        checkIn -> servicePointRepository.getServicePointById(getProperty(checkIn, SERVICE_POINT_ID)),
        representation::withServicePointName))
      .thenApply(r -> r.map(representation::withObjectAndAction))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private void getMany(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var checkInStorageRepository = new CheckInStorageRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var servicePointRepository = new ServicePointRepository(clients);
    final var representation = new CheckInRecordRepresentation();

    final var query = routingContext.request().query();

    checkInStorageRepository.findBy(query)
      .thenCompose(r -> r.after(checkIns -> enrichWithUsers(checkIns, userRepository, representation)))
      .thenCompose(r -> r.after(checkIns -> enrichWithServicePoints(checkIns, servicePointRepository, representation)))
      .thenApply(r -> r.map(checkIns -> checkIns.mapRecords(representation::withObjectAndAction)))
      .thenApply(r -> r.map(checkIns -> checkIns.asJson(identity(), RECORDS_PROPERTY_NAME)))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private static CompletableFuture<Result<MultipleRecords<JsonObject>>> enrichWithUsers(
    MultipleRecords<JsonObject> checkIns, UserRepository userRepository,
    CheckInRecordRepresentation representation) {

    final var userIds = distinctValuesOf(checkIns, PERFORMED_BY_USER_ID);

    if (userIds.isEmpty()) {
      return completedFuture(succeeded(checkIns));
    }

    return userRepository.getUsersForUserIds(userIds)
      .thenApply(r -> r.map(usersById -> checkIns.combineRecords(usersById,
        checkIn -> getProperty(checkIn, PERFORMED_BY_USER_ID),
        representation::withSource, null)));
  }

  private static CompletableFuture<Result<MultipleRecords<JsonObject>>> enrichWithServicePoints(
    MultipleRecords<JsonObject> checkIns, ServicePointRepository servicePointRepository,
    CheckInRecordRepresentation representation) {

    final var servicePointIds = distinctValuesOf(checkIns, SERVICE_POINT_ID);

    if (servicePointIds.isEmpty()) {
      return completedFuture(succeeded(checkIns));
    }

    return servicePointRepository.findServicePointsByIds(servicePointIds)
      .thenApply(r -> r.map(servicePoints -> {
        final var servicePointsById = servicePoints.stream()
          .collect(toMap(ServicePoint::getId, identity()));

        return checkIns.combineRecords(servicePointsById,
          checkIn -> getProperty(checkIn, SERVICE_POINT_ID),
          representation::withServicePointName, null);
      }));
  }

  private static List<String> distinctValuesOf(MultipleRecords<JsonObject> checkIns,
    String propertyName) {

    return checkIns.getRecords().stream()
      .map(checkIn -> getProperty(checkIn, propertyName))
      .filter(Objects::nonNull)
      .distinct()
      .toList();
  }
}
