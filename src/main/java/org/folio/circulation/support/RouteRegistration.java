package org.folio.circulation.support;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.server.ServerErrorResponse;

public class RouteRegistration {
  private final String rootPath;
  private final Router router;

  public RouteRegistration(String rootPath, Router router) {
    this.rootPath = rootPath;
    this.router = router;
  }

  public void get(Handler<RoutingContext> handler) {
    router.route(HttpMethod.GET, rootPath + "/:id").handler(handler)
      .failureHandler(this::failureResponder);
  }

  public void replace(Handler<RoutingContext> handler) {
    this.router.put(rootPath + "*").handler(BodyHandler.create());
    router.route(HttpMethod.PUT, rootPath + "/:id").handler(handler)
      .failureHandler(this::failureResponder);
  }

  public void delete(Handler<RoutingContext> handler) {
    router.route(HttpMethod.DELETE, rootPath + "/:id").handler(handler)
      .failureHandler(this::failureResponder);
  }

  public void deleteAll(Handler<RoutingContext> handler) {
    router.delete(rootPath).handler(handler)
      .failureHandler(this::failureResponder);
  }

  public void getMany(Handler<RoutingContext> handler) {
    router.get(rootPath).handler(handler)
      .failureHandler(this::failureResponder);
  }

  public void create(Handler<RoutingContext> handler) {
    router.post(rootPath + "*").handler(BodyHandler.create());
    router.post(rootPath).handler(handler)
      .failureHandler(this::failureResponder);
  }

  private void failureResponder(RoutingContext context) {
    Throwable failure = context.failure();

    if(failure != null) {
      if(StringUtils.isNotBlank(failure.getMessage())) {
        ServerErrorResponse.internalError(context.response(), failure.getMessage());
      }
      else {
        ServerErrorResponse.internalError(context.response(), failure.toString());
      }
    }
    else {
      ServerErrorResponse.internalError(context.response(), "Unknown failure occurred");
    }
  }
}
