package org.folio.circulation.resources;

import org.folio.circulation.domain.notice.schedule.ScheduledNoticeRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ScheduledNoticeProcessingResource extends Resource {

  public ScheduledNoticeProcessingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/process-scheduled-notices", router);

    routeRegistration.create(this::process);
  }

  private void process(RoutingContext routingContext) {

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final ScheduledNoticeRepository scheduledNoticeRepository =
      new ScheduledNoticeRepository(clients.scheduledNoticeStorageClient());

    scheduledNoticeRepository.findScheduledNoticesWithNextRunTimeLessThanNow();
    routingContext.response().end();
  }
}
