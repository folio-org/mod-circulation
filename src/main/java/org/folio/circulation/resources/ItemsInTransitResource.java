package org.folio.circulation.resources;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.services.ItemsInTransitReportService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;


public class ItemsInTransitResource extends Resource {
  private final String rootPath;
  private static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public ItemsInTransitResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    logger.info("[TRACE] -> register started");
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);
    routeRegistration.getMany(this::buildReport);
  }

  private void buildReport(RoutingContext routingContext) {
    logger.info("[TRACE] -> buildReport started");
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    new ItemsInTransitReportService(clients).buildReport()
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }
}
