package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.session.PatronActionType.ALL;
import static org.folio.circulation.support.results.AsynchronousResultBindings.safelyInitialise;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.notice.session.ExpiredSession;
import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.sessions.PatronActionSessionRepository;
import org.folio.circulation.infrastructure.storage.sessions.PatronExpiredSessionRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ExpiredSessionProcessingResource extends Resource {

  public ExpiredSessionProcessingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/notice-session-expiration-by-timeout", router);

    routeRegistration.create(this::process);
  }

  private void process(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final ConfigurationRepository configurationRepository
      = new ConfigurationRepository(clients);

    final var userRepository = new UserRepository(clients);
    final var itemRepository = new ItemRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final PatronActionSessionService patronSessionService
      = PatronActionSessionService.using(clients,
      PatronActionSessionRepository.using(clients, loanRepository, userRepository), loanRepository);

    final PatronExpiredSessionRepository patronExpiredSessionRepository
      = PatronExpiredSessionRepository.using(clients);

    safelyInitialise(configurationRepository::lookupSessionTimeout)
      .thenCompose(r -> r.after(this::defineExpiredTime))
      .thenCompose(r -> r.after(inactivityTime ->
        patronExpiredSessionRepository.findPatronExpiredSessions(ALL, inactivityTime)))
      .thenCompose(r -> r.after(expiredSessions -> attemptEndSessions(
        patronSessionService, expiredSessions)))
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .exceptionally(CommonFailures::failedDueToServerError)
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<ZonedDateTime>> defineExpiredTime(Integer timeout) {
    final ZonedDateTime now = ClockUtil.getZonedDateTime();
    Result<ZonedDateTime> dateTimeResult = Result.succeeded(now.minusMinutes(timeout));
    return CompletableFuture.completedFuture(dateTimeResult);
  }

  private CompletableFuture<Result<Void>> attemptEndSessions(
    PatronActionSessionService patronSessionService, List<ExpiredSession> expiredSessions) {

    //TODO: sessions without patronId will not be cleared?
    List<ExpiredSession> existingExpiredSessions = expiredSessions.stream()
      .filter(session -> StringUtils.isNotBlank(session.getPatronId()))
      .collect(Collectors.toList());

    if (existingExpiredSessions.isEmpty()) {
      return ofAsync(() -> null);
    }

    return patronSessionService.endExpiredSessions(expiredSessions);
  }
}
