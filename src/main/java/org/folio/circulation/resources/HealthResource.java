package org.folio.circulation.resources;

import org.folio.circulation.support.RouteRegistration;
import io.vertx.ext.web.Router;


public class HealthResource {
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration("/admin/health", router);
    routeRegistration.getMany(routingContext -> routingContext.end("OK"));
  }
}
