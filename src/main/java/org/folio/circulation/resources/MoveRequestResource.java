package org.folio.circulation.resources;

import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class MoveRequestResource extends Resource {
  public MoveRequestResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
        "/circulation/requests/:id/move", router);

    routeRegistration.create(this::moveRequest);
  }
  
  private void moveRequest(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    
    JsonObject representation = routingContext.getBodyAsJson();
    
    System.out.println("\n\n\ncontext: " + representation.toString() + "\n\n\n");
  }

}
