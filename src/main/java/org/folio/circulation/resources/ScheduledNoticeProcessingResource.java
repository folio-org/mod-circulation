package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.failed;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.ScheduledDueDateNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticesRepository;
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

public class ScheduledNoticeProcessingResource extends Resource {

  private static final int SCHEDULED_NOTICES_PROCESSING_LIMIT = 100;

  public ScheduledNoticeProcessingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/scheduled-notices-processing", router);

    routeRegistration.create(this::process);
  }

  private void process(RoutingContext routingContext) {
    final DateTime systemTime = DateTime.now(DateTimeZone.UTC);

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final ScheduledNoticesRepository scheduledNoticesRepository =
      ScheduledNoticesRepository.using(clients);

    final ScheduledDueDateNoticeHandler dueDateNoticeHandler =
      ScheduledDueDateNoticeHandler.using(clients, systemTime);

    scheduledNoticesRepository.findNoticesToSend(systemTime, SCHEDULED_NOTICES_PROCESSING_LIMIT)
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenCompose(r -> r.after(dueDateNoticeHandler::handleNotices))
      .thenApply(this::createWritableResult)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private ResponseWritableResult<Void> createWritableResult(Result<?> result) {
    if (result.failed()) {
      return failed(result.cause());
    } else {
      return new NoContentResult();
    }
  }
}
