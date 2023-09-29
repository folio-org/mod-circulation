package org.folio.circulation.resources;

import static org.folio.circulation.support.http.server.NoContentResponse.noContent;

import java.lang.invoke.MethodHandles;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.notice.session.PatronActionType;
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
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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
        log.warn("process:: end session failed: {}", result.cause());
        result.cause().writeTo(routingContext.response());
      } else {
        EndPatronSessionRequest endSessionRequest = result.value();
        String patronId = endSessionRequest.getPatronId();
        PatronActionType actionType = endSessionRequest.getActionType();
        patronActionSessionService.endSessions(patronId, actionType);
        log.info("process:: session ended successfully: patronId: {}, actionType: {}",
          patronId, actionType);
        noContent().writeTo(routingContext.response());
      }
    }
  }
}
