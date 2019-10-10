package org.folio.circulation.resources;

import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.representations.EndPatronSessionRequest;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class EndPatronActionSessionResource extends Resource {

  public EndPatronActionSessionResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/end-patron-action-session", router);
    routeRegistration.create(this::process);
  }

  private void process(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    PatronActionSessionService patronActionSessionService =
      PatronActionSessionService.using(clients);

    Result<EndPatronSessionRequest> endSessionRequestResult =
      EndPatronSessionRequest.from(routingContext.getBodyAsJson());

    if (endSessionRequestResult.failed()) {
      endSessionRequestResult.cause().writeTo(routingContext.response());
    } else {
      EndPatronSessionRequest endSessionRequest = endSessionRequestResult.value();
      patronActionSessionService.endSession(
        endSessionRequest.getPatronId(),
        endSessionRequest.getActionType());

      new NoContentResult().writeTo(routingContext.response());
    }
  }
}
