package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.failed;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.ConfigurationRepository;
import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.notice.session.PatronActionType;
import org.folio.circulation.domain.notice.session.PatronExpiredSessionRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;

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

    final ConfigurationRepository configurationRepository =
      new ConfigurationRepository(clients);

    final PatronActionSessionService patronSessionService = PatronActionSessionService.using(clients);
    final PatronExpiredSessionRepository patronExpiredSessionRepository = PatronExpiredSessionRepository.using(clients);

    configurationRepository.lookupSessionTimeout()
      .thenCompose(r -> r.after(this::defineExpiredTime))
      .thenCompose(r -> patronExpiredSessionRepository.findPatronExpiredSessions(PatronActionType.ALL, r.value().toString()))
      .thenCompose(r -> r.after(patronId -> attemptEndSession(patronSessionService, patronId))
        .thenApply(this::createWritableResult)
        .thenAccept(result -> result.writeTo(routingContext.response())));
  }

  private CompletableFuture<Result<DateTime>> defineExpiredTime(Integer timeout) {
    final DateTime now = ClockManager.getClockManager().getDateTime();
    Result<DateTime> dateTimeResult = Result.succeeded(now.minusMinutes(timeout));
    return CompletableFuture.completedFuture(dateTimeResult);
  }

  private CompletableFuture<Result<Void>> attemptEndSession(PatronActionSessionService patronSessionService, String patronId) {
    if (StringUtils.isBlank(patronId)) {
      return CompletableFuture.completedFuture(Result.succeeded(null));
    }
    return patronSessionService.endSession(patronId, PatronActionType.ALL);
  }

  private ResponseWritableResult<Void> createWritableResult(Result<?> result) {
    if (result.failed()) {
      return failed(result.cause());
    } else {
      return new NoContentResult();
    }
  }
}
