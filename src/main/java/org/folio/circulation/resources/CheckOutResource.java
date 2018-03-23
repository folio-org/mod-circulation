package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.support.CreatedJsonHttpResult;
import org.folio.circulation.support.RouteRegistration;

import java.util.UUID;

public class CheckOutResource extends CollectionResource {
  public CheckOutResource(HttpClient client) {
    super(client);
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration("/circulation/check-out", router);

    routeRegistration.create(this::checkOut);
  }

  private void checkOut(RoutingContext routingContext) {

    final JsonObject loan = new JsonObject();

    loan.put("id", UUID.randomUUID().toString());
    loan.put("status", new JsonObject().put("name", "Open"));

    new CreatedJsonHttpResult(loan).writeTo(routingContext.response());
  }
}
