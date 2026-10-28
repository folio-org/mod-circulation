package org.folio.circulation.resources;

import static java.util.function.Function.identity;
import static org.folio.circulation.infrastructure.storage.CheckInStorageRepository.RECORDS_PROPERTY_NAME;

import org.folio.circulation.infrastructure.storage.CheckInStorageRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CheckInCollectionResource extends Resource {
  private static final String ROOT_PATH = "/circulation/check-ins";

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

    final var id = routingContext.request().getParam("id");

    checkInStorageRepository.getById(id)
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private void getMany(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var checkInStorageRepository = new CheckInStorageRepository(clients);

    final var query = routingContext.request().query();

    checkInStorageRepository.findBy(query)
      .thenApply(r -> r.map(multipleRecords ->
        multipleRecords.asJson(identity(), RECORDS_PROPERTY_NAME)))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }
}
