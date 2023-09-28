package org.folio.circulation.resources;

import static org.folio.circulation.support.http.server.NoContentResponse.noContent;

import java.util.List;

import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.representations.EndPatronSessionRequest;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.sessions.PatronActionSessionRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

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

    final var userRepository = new UserRepository(clients);
    final var itemRepository = new ItemRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    PatronActionSessionService patronActionSessionService =
      PatronActionSessionService.using(clients,
        PatronActionSessionRepository.using(clients, loanRepository, userRepository), loanRepository);

    List<Result<EndPatronSessionRequest>> resultListOfEndSessionRequestResult =
      EndPatronSessionRequest.from(routingContext.getBodyAsJson());

    for (Result<EndPatronSessionRequest> result : resultListOfEndSessionRequestResult) {
      if (result.failed()) {
        result.cause().writeTo(routingContext.response());
      } else {
        EndPatronSessionRequest endSessionRequest = result.value();
        patronActionSessionService.endSessions(
          endSessionRequest.getPatronId(),
          endSessionRequest.getActionType());

        noContent().writeTo(routingContext.response());
      }
    }
  }
}
