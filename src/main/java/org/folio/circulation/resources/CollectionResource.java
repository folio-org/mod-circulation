package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.support.RouteRegistration;

abstract class CollectionResource extends Resource {
  private final String rootPath;

  CollectionResource(HttpClient client, String rootPath) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);

    routeRegistration.create(this::create);
    routeRegistration.get(this::get);
    routeRegistration.getMany(this::getMany);
    routeRegistration.replace(this::replace);
    routeRegistration.delete(this::delete);
    routeRegistration.deleteAll(this::empty);
  }

  abstract void create(RoutingContext routingContext);

  abstract void replace(RoutingContext routingContext);

  abstract void get(RoutingContext routingContext);

  abstract void delete(RoutingContext routingContext);

  abstract void getMany(RoutingContext routingContext);

  abstract void empty(RoutingContext routingContext);
}
