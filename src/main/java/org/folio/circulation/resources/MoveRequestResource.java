package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MoveRequestRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class MoveRequestResource extends Resource {
  public MoveRequestResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    router.put("/circulation/requests/:id/move"   ).handler(this::moveRequest);
  }

  private void moveRequest(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject representation = routingContext.getBodyAsJson();

    final String requestId = context.getStringParameter("id", "");
    final String destinationItemId = representation.getString("destinationItemId");
    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final RequestPolicyRepository requestPolicyRepository = new RequestPolicyRepository(clients);

    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients, null);
    final MoveRequestRecords moveRequestRecords = new MoveRequestRecords();

    completedFuture(succeeded(moveRequestRecords))
      .thenCombine(requestRepository.getById(requestId), this::addRequest)
      .thenCombine(requestRepository.getItem(requestId), this::addOriginalItem)
      .thenCombine(itemRepository.fetchById(destinationItemId), this::addDestinationItem)
      .thenCombine(requestQueueRepository.getByRequestId(requestId), this::addOriginalQueue)
      .thenCombine(requestQueueRepository.get(destinationItemId), this::addDestinationQueue)
      .thenCombine(requestPolicyRepository.lookupRequestPolicyByRequestId(requestId), this::addRequestPolicy)
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onMove))
//    Gets the request from MoveRequestRecords
      .thenApply(this::getRequest)
//    Converts the request to JSON for output
      .thenApply(r -> r.map(new RequestRepresentation()::extendedRepresentation))
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private Result<MoveRequestRecords> addRequest(
    Result<MoveRequestRecords> moveRequestRecords,
    Result<Request> request) {

    return Result.combine(moveRequestRecords, request,
      MoveRequestRecords::withRequest);
  }

  private Result<MoveRequestRecords> addOriginalItem(
    Result<MoveRequestRecords> moveRequestRecords,
    Result<Item> originalItem) {

    return Result.combine(moveRequestRecords, originalItem,
      MoveRequestRecords::withOriginalItem);
  }

  private Result<MoveRequestRecords> addDestinationItem(
    Result<MoveRequestRecords> moveRequestRecords,
    Result<Item> destinationItem) {

    return Result.combine(moveRequestRecords, destinationItem,
      MoveRequestRecords::withDestinationItem);
  }

  private Result<MoveRequestRecords> addOriginalQueue(
    Result<MoveRequestRecords> moveRequestRecords,
    Result<RequestQueue> originalQueue) {

    return Result.combine(moveRequestRecords, originalQueue,
      MoveRequestRecords::withOriginalRequestQueue);
  }

  private Result<MoveRequestRecords> addDestinationQueue(
    Result<MoveRequestRecords> moveRequestRecords,
    Result<RequestQueue> destinationQueue) {

    return Result.combine(moveRequestRecords, destinationQueue,
      MoveRequestRecords::withDestinationRequestQueue);
  }

  private Result<MoveRequestRecords> addRequestPolicy(
    Result<MoveRequestRecords> moveRequestRecords,
    Result<RequestPolicy> requestPolicy) {

    return Result.combine(moveRequestRecords, requestPolicy,
      MoveRequestRecords::withRequestPolicy);
  }

  private Result<Request> getRequest(Result<MoveRequestRecords> moveRequestRecords) {
    System.out.println("\n\n\nvalue: " + moveRequestRecords.value() + "\n\n\n");
    return of(() -> moveRequestRecords.value().getRequest());
  }
}
