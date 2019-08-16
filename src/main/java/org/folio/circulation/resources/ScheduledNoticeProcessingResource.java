package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.failed;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ConfigurationRepository;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticesRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public abstract class ScheduledNoticeProcessingResource extends Resource {

  private String rootPath;

  ScheduledNoticeProcessingResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);

    routeRegistration.create(this::process);
  }

  private void process(RoutingContext routingContext) {

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final ScheduledNoticesRepository scheduledNoticesRepository =
      ScheduledNoticesRepository.using(clients);
    final ConfigurationRepository configurationRepository =
      new ConfigurationRepository(clients);

    configurationRepository.lookupSchedulerNoticesProcessingLimit()
      .thenCompose(r -> r.after(limit -> findNoticesToSend(scheduledNoticesRepository, limit)))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenCompose(r -> r.after(notices -> handleNotices(clients, notices)))
      .thenApply(this::createWritableResult)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  protected abstract CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ScheduledNoticesRepository scheduledNoticesRepository, int limit);

  protected abstract CompletableFuture<Result<Collection<ScheduledNotice>>> handleNotices(
    Clients clients, Collection<ScheduledNotice> noticesResult);

  private ResponseWritableResult<Void> createWritableResult(Result<?> result) {
    if (result.failed()) {
      return failed(result.cause());
    } else {
      return new NoContentResult();
    }
  }
}
