package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.PrintEventRequest;
import org.folio.circulation.infrastructure.storage.PrintEventsRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;

import java.lang.invoke.MethodHandles;

import static org.folio.circulation.support.results.Result.ofAsync;

public class PrintEventsResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public PrintEventsResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/print-events", router)
      .create(this::create);
  }

  void create(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var printEventsRepository = new PrintEventsRepository(clients);

    final var incomingRepresentation = routingContext.body().asJsonObject();
    final var printEventRequest = PrintEventRequest.from(incomingRepresentation);

    log.info("create:: Creating print event: {}", () -> printEventRequest);

    ofAsync(printEventRequest)
      .thenCompose(r -> r.after(printEventsRepository::create))
      .thenApply(r -> r.map(response -> JsonHttpResponse.created(null, null)))
      .thenAccept(context::writeResultToHttpResponse);
  }
}
