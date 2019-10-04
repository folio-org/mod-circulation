package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.reorder.ReorderQueueRequest;
import org.folio.circulation.domain.validation.RequestQueueValidation;
import org.folio.circulation.resources.context.ReorderRequestContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.json.JsonObject;

public class RequestQueueResource extends Resource {
  private static final String CIRCULATION_REQUESTS_QUEUE_URI = "/circulation/requests/queue/";

  public RequestQueueResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    //TODO: Replace with route registration, for failure handling
    router.get(CIRCULATION_REQUESTS_QUEUE_URI + ":itemId").handler(this::getMany);

    new RouteRegistration(CIRCULATION_REQUESTS_QUEUE_URI + ":itemId/reorder", router)
      .create(this::reorder);
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

  private void reorder(RoutingContext context) {
    ReorderRequestContext reorderContext = new ReorderRequestContext(
      context.request().getParam("itemId"),
      context.getBodyAsJson().mapTo(ReorderQueueRequest.class)
    );

    final Clients clients = Clients.create(new WebContext(context), client);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final UpdateRequestQueue updateRequestQueue = new UpdateRequestQueue(
      requestQueueRepository, requestRepository, new ServicePointRepository(clients)
    );

    requestQueueRepository.get(reorderContext.getItemId())
      .thenApply(r -> r.map(reorderContext::withRequestQueue))
      // Validation block
      .thenApply(RequestQueueValidation::queueFoundForItem)
      .thenApply(RequestQueueValidation::queueIsConsistent)
      .thenApply(RequestQueueValidation::pageRequestHasFirstPosition)
      .thenApply(RequestQueueValidation::fulfillingRequestHasFirstPosition)
      // Business logic block
      .thenCompose(updateRequestQueue::onReorder)
      .thenCompose(r -> r.after(this::toRepresentation))
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(r -> r.writeTo(context.response()));
  }

  private CompletableFuture<Result<JsonObject>> toRepresentation(ReorderRequestContext context) {
    final RequestRepresentation requestRepresentation = new RequestRepresentation();

    return completedFuture(Result.succeeded(context.getRequestQueue()))
      .thenApply(r -> r.map(queue -> new MultipleRecords<>(queue.getRequests(), queue.size())))
      .thenApply(r -> r.map(requests -> requests
        .asJson(
          requestRepresentation::extendedRepresentation,
          "requests"
        )
      ));
  }
}
