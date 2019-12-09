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

import java.util.List;

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

    List<Result<EndPatronSessionRequest>> resultListOfEndSessionRequestResult =
      EndPatronSessionRequest.from(routingContext.getBodyAsJson());

    for (Result<EndPatronSessionRequest> result : resultListOfEndSessionRequestResult) {
      if (result.failed()) {
        result.cause().writeTo(routingContext.response());
      } else {
        EndPatronSessionRequest endSessionRequest = result.value();
        patronActionSessionService.endSession(
          endSessionRequest.getPatronId(),
          endSessionRequest.getActionType());

        new NoContentResult().writeTo(routingContext.response());
      }
    }
  }
}
