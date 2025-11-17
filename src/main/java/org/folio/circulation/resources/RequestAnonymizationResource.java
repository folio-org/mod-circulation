package org.folio.circulation.resources;

import java.lang.invoke.MethodHandles;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.RequestAnonymizationService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestAnonymizationResource extends Resource {
  public RequestAnonymizationResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    final RouteRegistration rr =
      new RouteRegistration("/request-anonymization/:requestId", router);
    rr.create(this::anonymizeRequest);
  }

  public void anonymizeRequest(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final String requestId = routingContext.request().getParam("requestId");

    final var eventPublisher = new EventPublisher(clients);
    final var requestAnonymizationService = new RequestAnonymizationService(clients, eventPublisher);

    requestAnonymizationService.anonymizeSingle(requestId, context.getUserId())
      .thenApply(r -> r.map(id ->
        JsonHttpResponse.ok(new JsonObject()
          .put("requestId", id)
          .put("anonymized", true))
      ))
      .thenAccept(context::writeResultToHttpResponse);
  }
}
