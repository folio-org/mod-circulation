package org.folio.circulation.resources;

import static org.folio.circulation.support.http.server.JsonHttpResponse.created;
import static org.folio.circulation.support.http.server.NoContentResponse.noContent;
import static org.folio.util.PubSubModuleRegistrationUtil.registerLogEventPublisher;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import org.folio.circulation.services.PubSubRegistrationService;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.Map;

public class TenantActivationResource {

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration("/_/tenant", router);
    routeRegistration.create(this::enableModuleForTenant);
    routeRegistration.deleteAll(this::disableModuleForTenant);
  }

  public void enableModuleForTenant(RoutingContext routingContext) {
    Map<String, String> headers = new WebContext(routingContext).getHeaders();
    Vertx vertx = routingContext.vertx();
    HttpServerResponse response = routingContext.response();
    PubSubRegistrationService.registerModule(headers, vertx)
      .thenAccept(result -> registerLogEventPublisher(headers, vertx)
        .thenRun(() -> created(new JsonObject()).writeTo(response)))
      .exceptionally(throwable -> {
        ServerErrorResponse.internalError(response, throwable.getLocalizedMessage());
        return null;
      });
  }

  public void disableModuleForTenant(RoutingContext routingContext) {
    PubSubRegistrationService.unregisterModule(new WebContext(routingContext).getHeaders(),
      routingContext.vertx())
      .thenRun(() -> noContent().writeTo(routingContext.response()))
      .exceptionally(throwable -> {
        ServerErrorResponse.internalError(routingContext.response(),
          throwable.getLocalizedMessage());
        return null;
      });
  }
}
