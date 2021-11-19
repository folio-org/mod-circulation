package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.logs.RequestUpdateLogEventMapper.mapToRequestLogEventJson;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.reorder.ReorderQueueRequest;
import org.folio.circulation.domain.representations.logs.LogEventType;
import org.folio.circulation.domain.validation.RequestQueueValidation;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.resources.context.ReorderRequestContext;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestQueueResource extends Resource {
  public RequestQueueResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    final String circulationRequestQueueForItemUri = "/circulation/requests/queue/";
    final String circulationRequestQueueForInstanceUri = "/circulation/requests/instance/queue/";

    //TODO: Replace with route registration, for failure handling
    router.get(circulationRequestQueueForItemUri + ":itemId").handler(this::getQueueForItem);
    router.get(circulationRequestQueueForInstanceUri + ":instanceId").handler(
      this::getQueueForInstance);

    new RouteRegistration(circulationRequestQueueForItemUri + ":itemId/reorder", router)
      .create(this::reorderQueueForItem);
    new RouteRegistration(circulationRequestQueueForInstanceUri + ":instanceId/reorder",
      router).create(this::reorderQueueForInstance);
  }

  private void getQueueForInstance(RoutingContext routingContext) {
    getQueue(routingContext, true);
  }

  private void reorderQueueForInstance(RoutingContext routingContext) {
    ReorderRequestContext reorderContext = new ReorderRequestContext(
      routingContext.request().getParam("instanceId"), null,
      routingContext.getBodyAsJson().mapTo(ReorderQueueRequest.class));

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);

    final UpdateRequestQueue updateRequestQueue = new UpdateRequestQueue(
      requestQueueRepository, requestRepository, new ServicePointRepository(clients),
      new ConfigurationRepository(clients));

    requestQueueRepository.getByInstanceId(reorderContext.getItemId())
      .thenApply(r -> r.map(reorderContext::withRequestQueue))
      // Validation block
      .thenApply(RequestQueueValidation::queueIsFound)
      .thenApply(RequestQueueValidation::positionsAreSequential)
      .thenApply(RequestQueueValidation::queueIsConsistent)
      .thenApply(RequestQueueValidation::pageRequestHasFirstPosition)
      .thenApply(RequestQueueValidation::fulfillingRequestHasFirstPosition)
      // Business logic block
      .thenCompose(updateRequestQueue::onReorder)
      .thenApply(q -> publishReorderedQueue(eventPublisher, q))
      .thenCompose(r -> r.after(this::toRepresentation))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private void getQueueForItem(RoutingContext routingContext) {
    getQueue(routingContext, false);
  }

  private void reorderQueueForItem(RoutingContext routingContext) {
    ReorderRequestContext reorderContext = new ReorderRequestContext(
      null, routingContext.request().getParam("itemId"),
      routingContext.getBodyAsJson().mapTo(ReorderQueueRequest.class));

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);

    final UpdateRequestQueue updateRequestQueue = new UpdateRequestQueue(
      requestQueueRepository, requestRepository, new ServicePointRepository(clients),
      new ConfigurationRepository(clients));

    requestQueueRepository.getByItemId(reorderContext.getItemId())
      .thenApply(r -> r.map(reorderContext::withRequestQueue))
      // Validation block
      .thenApply(RequestQueueValidation::queueIsFound)
      .thenApply(RequestQueueValidation::positionsAreSequential)
      .thenApply(RequestQueueValidation::queueIsConsistent)
      .thenApply(RequestQueueValidation::pageRequestHasFirstPosition)
      .thenApply(RequestQueueValidation::fulfillingRequestHasFirstPosition)
      // Business logic block
      .thenCompose(updateRequestQueue::onReorder)
      .thenApply(q -> publishReorderedQueue(eventPublisher, q))
      .thenCompose(r -> r.after(this::toRepresentation))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private void getQueue(RoutingContext routingContext, boolean queueForInstance) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final RequestRepresentation requestRepresentation = new RequestRepresentation();

    CompletableFuture<Result<RequestQueue>> requestQueue;
    if (queueForInstance) {
      String instanceId = routingContext.request().getParam("instanceId");
      requestQueue = requestQueueRepository.getByInstanceId(instanceId);
    }
    else {
      String itemId = routingContext.request().getParam("itemId");
      requestQueue = requestQueueRepository.getByItemId(itemId);
    }

    requestQueue
      .thenApply(r -> r.map(queue -> new MultipleRecords<>(queue.getRequests(), queue.size())))
      .thenApply(r -> r.map(requests ->
        requests.asJson(requestRepresentation::extendedRepresentation, "requests")))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private Result<ReorderRequestContext> publishReorderedQueue(EventPublisher eventPublisher, Result<ReorderRequestContext> reorderRequestContext) {
    reorderRequestContext.after(r -> {
      CompletableFuture.runAsync(() -> {
        List<Request> reordered = r.getReorderRequestToRequestMap().values().stream().filter(Request::hasChangedPosition).collect(Collectors.toList());
        eventPublisher.publishLogRecord(mapToRequestLogEventJson(reordered), LogEventType.REQUEST_REORDERED);
      });
      return null;
    });
    return reorderRequestContext;
  }

  private CompletableFuture<Result<JsonObject>> toRepresentation(ReorderRequestContext context) {
    final RequestRepresentation requestRepresentation = new RequestRepresentation();

    return completedFuture(Result.succeeded(context.getRequestQueue()))
      .thenApply(r -> r.map(queue -> new MultipleRecords<>(queue.getRequests(), queue.size())))
      .thenApply(r -> r.map(requests -> requests
        .asJson(requestRepresentation::extendedRepresentation, "requests")));
  }
}
