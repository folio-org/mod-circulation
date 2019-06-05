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
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.ItemRepository;
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
    final String requestStatus = representation.getString("requestStatus");

    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final RequestRepository requestRepository = RequestRepository.using(clients);


    final MoveRequestRecords moveRequestRecords =
      new MoveRequestRecords();

    completedFuture(succeeded(moveRequestRecords))
      .thenCombineAsync(requestRepository.getById(requestId), this::addRequest)
      .thenCombineAsync(itemRepository.fetchByRequestId(requestId), this::addOriginalItem)
      .thenCombineAsync(itemRepository.fetchById(destinationItemId), this::addDestinationItem)
      .thenCombineAsync(requestQueueRepository.getByRequestId(requestId), this::addOriginalQueue)
      .thenCombineAsync(requestQueueRepository.get(destinationItemId), this::addDestinationQueue)
      .thenApply(this::updateRequestItem)
      .thenCombineAsync(completedFuture(succeeded(requestStatus)), this::updateRequestStatus)
      .thenApply(this::getRequest)
//    Converts the request to JSON for output
      .thenApply(r -> r.map(new RequestRepresentation()::extendedRepresentation))
      .thenApply(CreatedJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));

    System.out.println("\n\n\ndestinationItemId: " + destinationItemId + "\n\n\n");
    System.out.println("\n\n\nrequest id: " + requestId + "\n\n\n");
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

  private Result<MoveRequestRecords> updateRequestItem(
    Result<MoveRequestRecords> moveRequestRecords) {

    Item destinationItem = moveRequestRecords.value().getDestinationItem();
    Request request = moveRequestRecords.value().getRequest();

    Result<Request> updatedRequest = Result.of(() -> request.withItem(destinationItem));

    return Result.combine(moveRequestRecords, updatedRequest,
      MoveRequestRecords::withRequest);
  }

  private Result<MoveRequestRecords> updateRequestStatus(
    Result<MoveRequestRecords> moveRequestRecords,
    Result<String> requestStatus) {

    Request request = moveRequestRecords.value().getRequest();
    RequestStatus status = RequestStatus.from(requestStatus.value());
    request.changeStatus(status);

    Result<Request> updatedRequest = Result.of(() -> request);

    return Result.combine(moveRequestRecords, updatedRequest,
        MoveRequestRecords::withRequest);
  }

  private Result<Request> getRequest(Result<MoveRequestRecords> moveRequestRecords) {
    return of(() -> moveRequestRecords.value().getRequest());
  }
}
