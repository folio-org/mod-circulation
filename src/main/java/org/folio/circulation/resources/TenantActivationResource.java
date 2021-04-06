package org.folio.circulation.resources;

import static org.folio.circulation.support.http.server.JsonHttpResponse.created;
import static org.folio.circulation.support.http.server.NoContentResponse.noContent;

import java.util.Map;

import org.folio.circulation.services.PubSubRegistrationService;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class TenantActivationResource {

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration("/_/tenant", router);
    routeRegistration.create(this::enableModuleForTenant);
    routeRegistration.deleteAll(ctx -> noContent().writeTo(ctx.response()));
  }

  private void enableModuleForTenant(RoutingContext routingContext) {
    Map<String, String> headers = new WebContext(routingContext).getHeaders();
    PubSubRegistrationService.registerModule(headers,routingContext.vertx())
      .thenRun(() -> created(new JsonObject()).writeTo(routingContext.response()))
      .exceptionally(throwable -> {
        ServerErrorResponse.internalError(routingContext.response(), throwable.getLocalizedMessage());
        return null;
      });
  }
}
