package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ServerErrorFailure;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class RenewByIdResource extends Resource {
  public RenewByIdResource(HttpClient client) {
    super(client);
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/renew-by-id", router);

    routeRegistration.create(this::renew);
  }

  private void renew(RoutingContext routingContext) {
    completedFuture(HttpResult.failure(new ServerErrorFailure("Not Implemented")))
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }
}
