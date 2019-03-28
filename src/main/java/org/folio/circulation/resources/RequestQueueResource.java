package org.folio.circulation.resources;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestQueueResource extends Resource {
  public RequestQueueResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    //TODO: Replace with route registration, for failure handling
    router.get("/circulation/requests/queue/:itemId").handler(this::getMany);
  }

  private void getMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final RequestRepresentation requestRepresentation = new RequestRepresentation();

    String itemId = routingContext.request().getParam("itemId");

    requestQueueRepository.get(itemId)
      .thenApply(r -> r.map(requestQueue -> new MultipleRecords<>(
        requestQueue.getRequests(), requestQueue.size())))
      .thenApply(r -> r.map(requests ->
        requests.asJson(requestRepresentation::extendedRepresentation, "requests")))
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }
}
