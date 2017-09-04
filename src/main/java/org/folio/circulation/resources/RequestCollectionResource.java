package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.circulation.support.http.server.ServerErrorResponse;

public class RequestCollectionResource {

  private final String rootPath;

  public RequestCollectionResource(String rootPath) {
    this.rootPath = rootPath;
  }

  public void register(Router router) {
    router.post(rootPath + "*").handler(BodyHandler.create());
    router.put(rootPath + "*").handler(BodyHandler.create());

    router.post(rootPath).handler(this::create);
    router.get(rootPath).handler(this::getMany);
    router.delete(rootPath).handler(this::empty);

    router.route(HttpMethod.GET, rootPath + "/:id").handler(this::get);
    router.route(HttpMethod.PUT, rootPath + "/:id").handler(this::replace);
    router.route(HttpMethod.DELETE, rootPath + "/:id").handler(this::delete);
  }

  private void create(RoutingContext routingContext) {
    ServerErrorResponse.notImplemented(routingContext.response());
  }

  private void replace(RoutingContext routingContext) {
    ServerErrorResponse.notImplemented(routingContext.response());
  }

  private void get(RoutingContext routingContext) {
    ServerErrorResponse.notImplemented(routingContext.response());
  }

  private void delete(RoutingContext routingContext) {
    ServerErrorResponse.notImplemented(routingContext.response());
  }

  private void getMany(RoutingContext routingContext) {
    ServerErrorResponse.notImplemented(routingContext.response());
  }

  private void empty(RoutingContext routingContext) {
    ServerErrorResponse.notImplemented(routingContext.response());
  }
}
