package org.folio.circulation.support.http.server;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class VertxCollectionResource {

  private final String rootPath;

  public VertxCollectionResource(String rootPath) {

    this.rootPath = rootPath;
  }

  public void register(Router router) {
    router.post(rootPath + "*").handler(BodyHandler.create());
    router.post(rootPath).handler(this::create);
  }

  private void create(RoutingContext routingContext) {
    JsonResponse.created(routingContext.response(),
      routingContext.getBodyAsJson(), 201);
  }
}
