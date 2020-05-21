package org.folio.circulation.resources;

import static org.folio.circulation.support.http.server.JsonHttpResponse.created;
import static org.folio.circulation.support.http.server.JsonHttpResponse.noContent;

import java.util.Map;
import java.util.stream.Collectors;

import org.folio.circulation.services.PubSubService;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.ServerErrorResponse;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class TenantActivationResource {
  private static final Logger logger = LoggerFactory.getLogger(TenantActivationResource.class);

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration("/_/tenant", router);
    routeRegistration.create(this::enableModuleForTenant);
    routeRegistration.deleteAll(this::disableModuleForTenant);
  }

  public void enableModuleForTenant(RoutingContext routingContext) {
    Map<String, String> headers = routingContext.request().headers().entries().stream()
      .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));

    Vertx vertx = routingContext.vertx();

    Promise<Object> promise = Promise.promise();
    promise.future().setHandler(ar -> {
      if (ar.succeeded()) {
        created(new JsonObject()).writeTo(routingContext.response());
      }
      else {
        ServerErrorResponse.internalError(routingContext.response(),
          ar.cause().getLocalizedMessage());
      }
    });
    PubSubService.registerModule(headers, vertx, promise);
  }

  public void disableModuleForTenant(RoutingContext routingContext) {
    Map<String, String> headers = routingContext.request().headers().entries().stream()
      .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));

    Vertx vertx = routingContext.vertx();

    Promise<Object> promise = Promise.promise();
    promise.future().setHandler(ar -> {
      if (ar.succeeded()) {
        noContent().writeTo(routingContext.response());
      }
      else {
        ServerErrorResponse.internalError(routingContext.response(),
          ar.cause().getLocalizedMessage());
      }
    });
    PubSubService.unregisterModule(headers, vertx, promise);
  }
}
