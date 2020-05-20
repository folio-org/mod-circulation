package org.folio.circulation.resources;

import static org.folio.circulation.support.http.server.JsonHttpResponse.created;
import static org.folio.circulation.support.http.server.JsonHttpResponse.noContent;

import java.util.Map;
import java.util.stream.Collectors;

import org.folio.circulation.services.PubSubService;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.ServerErrorResponse;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CirculationTenantAPI {
  private static final Logger logger = LoggerFactory.getLogger(CirculationTenantAPI.class);

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration("/_/tenant", router);
    routeRegistration.create(this::postTenant);
    routeRegistration.deleteAll(this::deleteTenant);
  }

  public void postTenant(RoutingContext routingContext) {
    Map<String, String> headers = routingContext.request().headers().entries().stream()
      .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));

    Vertx vertx = routingContext.vertx();

    vertx.executeBlocking(
      promise -> PubSubService.registerModule(headers, vertx, promise),
      result -> {
        if (result.failed()) {
          ServerErrorResponse.internalError(routingContext.response(),
            result.cause().getLocalizedMessage());
        }
        else {
          created(new JsonObject()).writeTo(routingContext.response());
        }
      }
    );
  }

  public void deleteTenant(RoutingContext routingContext) {
    Map<String, String> headers = routingContext.request().headers().entries().stream()
      .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));

    Vertx vertx = routingContext.vertx();

    vertx.executeBlocking(
      promise -> PubSubService.unregisterModule(headers, vertx, promise),
      result -> {
        if (result.failed()) {
          ServerErrorResponse.internalError(routingContext.response(),
            result.cause().getLocalizedMessage());
        }
        else {
          noContent().writeTo(routingContext.response());
        }
      }
    );
  }
}
