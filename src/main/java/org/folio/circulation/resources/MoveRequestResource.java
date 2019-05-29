package org.folio.circulation.resources;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.ResponseWritableResult;
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
    
    Request request = requestRepository.getById(requestId);
    final Item originalItem = itemRepository.fetchById(itemId);
    final RequestQueue queue = requestQueueRepository.get(itemRelatedRecord);
    
    requestRepository.getById(requestId)
      .thenCombineAsync(itemRepository.fetchById(destinationItemId), this::updateItem)
      .thenApply(this::updateStatus)
      .thenComposeAsync(r -> r.after(requestRepository::update))
      .thenApply(this::updatedRequestFrom)
      .thenAccept(result -> result.writeTo(routingContext.response()));
    
    System.out.println("\n\n\ndestinationItemId: " + destinationItemId + "\n\n\n");
    System.out.println("\n\n\nrequest id: " + requestId + "\n\n\n");
  }
  
  private Result<Request> updateItem(
    Result<Request> requestResult,
    Result<Item> itemResult) {

    return Result.combine(requestResult, itemResult,
      Request::withItem);
  }
  
  private Result<Request> updateStatus(Result<Request> requestResult) {
    
    
  }
  
  private String calculateNewStatus(Result<Request> requestResult) {
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    RequestQueue queue = requestQ
  }
  
  private ResponseWritableResult<JsonObject> updatedRequestFrom(Result<Request> result) {
    if(result.failed()) {
      return Result.failed(result.cause());
    }
    else {
      return new CreatedJsonResponseResult(result.value().asJson(), null);
    }
  }

}
