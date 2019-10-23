package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.failed;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ConfigurationRepository;
import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.notice.session.PatronActionType;
import org.folio.circulation.domain.notice.session.PatronExpiredSessionRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

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
      "/circulation/expired-session-processing", router);

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
      .thenCompose(r -> patronExpiredSessionRepository.findPatronExpiredSessions(PatronActionType.CHECK_OUT, r.value().toString()))
      .thenCompose(r -> r.after(patronId -> patronSessionService.endSession(patronId, PatronActionType.CHECK_OUT))
      .thenApply(this::createWritableResult)
      .thenAccept(result -> result.writeTo(routingContext.response())));
  }

  private CompletableFuture<Result<DateTime>> defineExpiredTime(Integer timeout) {
    Result<DateTime> dateTimeResult = Result.succeeded(DateTime.now(DateTimeZone.UTC).minusMinutes(timeout));
    return CompletableFuture.completedFuture(dateTimeResult);
  }

  private ResponseWritableResult<Void> createWritableResult(Result<?> result) {
    if (result.failed()) {
      return failed(result.cause());
    } else {
      return new NoContentResult();
    }
  }
}
