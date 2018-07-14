package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.*;
import org.folio.circulation.domain.representations.RequestProperties;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.ClientErrorResponse;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.SuccessResponse;
import org.folio.circulation.support.http.server.WebContext;

import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.*;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

public class RequestCollectionResource extends CollectionResource {
  public RequestCollectionResource(HttpClient client) {
    super(client, "/circulation/requests");
  }

  void create(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject representation = routingContext.getBodyAsJson();

    RequestStatus status = RequestStatus.from(representation);

    HttpServerResponse response = routingContext.response();
    if(!status.isValid()) {
      ClientErrorResponse.badRequest(response,
        RequestStatus.invalidStatusErrorMessage());
      return;
    }
    else {
      status.writeTo(representation);
    }

    removeRelatedRecordInformation(representation);

    final Request request = Request.from(representation);

    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, true, false);
    final RequestQueueRepository requestQueueRepository = new RequestQueueRepository(clients);
    final UserRepository userRepository = new UserRepository(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateLoanActionHistory updateLoanActionHistory = new UpdateLoanActionHistory(clients);
    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> failure(
      "proxyUserId is not valid", RequestProperties.PROXY_USER_ID,
      request.getProxyUserId()));

    completedFuture(succeeded(new RequestAndRelatedRecords(request)))
      .thenCombineAsync(itemRepository.fetchFor(request), this::addInventoryRecords)
      .thenApply(this::refuseWhenItemDoesNotExist)
      .thenApply(this::refuseWhenItemIsNotValid)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueRepository.get(request.getItemId()), this::addRequestQueue)
      .thenCombineAsync(userRepository.getUser(request.getUserId(), false), this::addUser)
      .thenCombineAsync(userRepository.getUser(request.getProxyUserId(), false), this::addProxyUser)
      .thenApply(r -> r.next(this::setRequestQueuePosition))
      .thenComposeAsync(r -> r.after(updateItem::onRequestCreation))
      .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestCreation))
      .thenComposeAsync(r -> r.after(records -> createRequest(records, clients)))
      .thenApply(r -> r.map(this::extendedRequest))
      .thenApply(CreatedJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void replace(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");
    JsonObject representation = routingContext.getBodyAsJson();

    removeRelatedRecordInformation(representation);

    final Request request = Request.from(representation);

    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, false, false);
    final UserRepository userRepository = new UserRepository(clients);
    final RequestQueueRepository requestQueueRepository = new RequestQueueRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> failure(
      "proxyUserId is not valid", RequestProperties.PROXY_USER_ID,
      request.getProxyUserId()));

    completedFuture(succeeded(new RequestAndRelatedRecords(request)))
      .thenCombineAsync(itemRepository.fetchFor(request), this::addInventoryRecords)
      .thenCombineAsync(userRepository.getUser(request.getUserId(), false), this::addUser)
      .thenCombineAsync(userRepository.getUser(request.getProxyUserId(), false), this::addProxyUser)
      .thenCombineAsync(requestQueueRepository.get(request.getItemId()), this::addRequestQueue)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenApply(r -> r.next(this::removeRequestQueuePositionWhenCancelled))
      .thenComposeAsync(result -> result.after(requestAndRelatedRecords ->
        updateRequest(clients, requestAndRelatedRecords)))
      .thenApply(NoContentHttpResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private CompletableFuture<HttpResult<RequestAndRelatedRecords>> updateRequest(
    Clients clients,
    RequestAndRelatedRecords requestAndRelatedRecords) {

    CompletableFuture<HttpResult<RequestAndRelatedRecords>> requestUpdated =
      new CompletableFuture<>();

    final Item item = requestAndRelatedRecords.getInventoryRecords();
    final User requester = requestAndRelatedRecords.getRequestingUser();
    final User proxy = requestAndRelatedRecords.getProxyUser();

    final Request request = requestAndRelatedRecords.getRequest();

    final JsonObject representation = new RequestRepresentation().storedRequest(request, item, requester, proxy
    );

    clients.requestsStorage().put(request.getId(), representation, response -> {
      if(response.getStatusCode() == 204) {
        requestUpdated.complete(succeeded(requestAndRelatedRecords));
      }
      else {
        requestUpdated.complete(failed(new ForwardOnFailure(response)));
      }
    });

    return requestUpdated;
  }

  void get(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final RequestRepository requestRepository = RequestRepository.using(clients);

    String id = routingContext.request().getParam("id");

    requestRepository.getById(id)
      .thenApply(r -> r.map(this::toRepresentation))
      .thenApply(OkJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    String id = routingContext.request().getParam("id");

    clients.requestsStorage().delete(id, response -> {
      if(response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
  }

  void getMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final RequestRepository requestRepository = RequestRepository.using(clients);

    requestRepository.findBy(routingContext.request().query())
      .thenApply(r -> r.map(requests ->
        requests.asJson(this::toRepresentation, "requests")))
      .thenApply(OkJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.requestsStorage().delete(response -> {
      if(response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
  }

  private JsonObject toRepresentation(Request request) {
    final JsonObject requestRepresentation = request.asJson();
    addAdditionalItemProperties(requestRepresentation, request.getItem());

    return requestRepresentation;
  }

  private void addAdditionalItemProperties(
    JsonObject request,
    Item item) {

    if(item == null || item.isNotFound()) {
      return;
    }

    JsonObject itemSummary = request.containsKey("item")
      ? request.getJsonObject("item")
      : new JsonObject();

    write(itemSummary, "holdingsRecordId", item.getHoldingsRecordId());
    write(itemSummary, "instanceId", item.getInstanceId());

    final JsonObject location = item.getLocation();

    if(location != null && location.containsKey("name")) {
      itemSummary.put("location", new JsonObject()
        .put("name", location.getString("name")));
    }

    request.put("item", itemSummary);
  }

  private void removeRelatedRecordInformation(JsonObject request) {
    request.remove("item");
    request.remove("requester");
    request.remove("proxy");
  }

  private HttpResult<RequestAndRelatedRecords> addInventoryRecords(
    HttpResult<RequestAndRelatedRecords> loanResult,
    HttpResult<Item> inventoryRecordsResult) {

    return HttpResult.combine(loanResult, inventoryRecordsResult,
      RequestAndRelatedRecords::withInventoryRecords);
  }

  private HttpResult<RequestAndRelatedRecords> addRequestQueue(
    HttpResult<RequestAndRelatedRecords> loanResult,
    HttpResult<RequestQueue> requestQueueResult) {

    return HttpResult.combine(loanResult, requestQueueResult,
      RequestAndRelatedRecords::withRequestQueue);
  }

  private HttpResult<RequestAndRelatedRecords> addUser(
    HttpResult<RequestAndRelatedRecords> loanResult,
    HttpResult<User> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      RequestAndRelatedRecords::withRequestingUser);
  }

  private HttpResult<RequestAndRelatedRecords> addProxyUser(
    HttpResult<RequestAndRelatedRecords> loanResult,
    HttpResult<User> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      RequestAndRelatedRecords::withProxyUser);
  }

  private HttpResult<RequestAndRelatedRecords> refuseWhenItemDoesNotExist(
    HttpResult<RequestAndRelatedRecords> result) {

    return result.next(requestAndRelatedRecords -> {
      if(requestAndRelatedRecords.getInventoryRecords().isNotFound()) {
        return failed(failure(
          "Item does not exist", "itemId",
          requestAndRelatedRecords.getRequest().getItemId()));
      }
      else {
        return result;
      }
    });
  }

  private HttpResult<RequestAndRelatedRecords> refuseWhenItemIsNotValid(
    HttpResult<RequestAndRelatedRecords> result) {

    return result.next(requestAndRelatedRecords -> {
      Request request = requestAndRelatedRecords.getRequest();

      RequestType requestType = RequestType.from(request);

      if (!requestType.canCreateRequestForItem(requestAndRelatedRecords.getInventoryRecords())) {
        return failed(failure(
          String.format("Item is not %s, %s or %s", CHECKED_OUT,
            CHECKED_OUT_HELD, CHECKED_OUT_RECALLED),
          "itemId", request.getItemId()
        ));
      }
      else {
        return result;
      }
    });
  }

  private CompletableFuture<HttpResult<RequestAndRelatedRecords>> createRequest(
    RequestAndRelatedRecords requestAndRelatedRecords,
    Clients clients) {

    CompletableFuture<HttpResult<RequestAndRelatedRecords>> onCreated = new CompletableFuture<>();

    final Request request = requestAndRelatedRecords.getRequest();
    final User requestingUser = requestAndRelatedRecords.getRequestingUser();
    final User proxyUser = requestAndRelatedRecords.getProxyUser();

    JsonObject representation = new RequestRepresentation().storedRequest(request,
      requestAndRelatedRecords.getInventoryRecords(), requestingUser, proxyUser
    );

    clients.requestsStorage().post(representation, response -> {
      if (response.getStatusCode() == 201) {
        onCreated.complete(succeeded(
          requestAndRelatedRecords.withRequest(Request.from(response.getJson()))));
      } else {
        onCreated.complete(failed(new ForwardOnFailure(response)));
      }
    });

    return onCreated;
  }

  private JsonObject extendedRequest(RequestAndRelatedRecords requestAndRelatedRecords) {
    final JsonObject representation = requestAndRelatedRecords.getRequest().asJson();

    addAdditionalItemProperties(representation,
      requestAndRelatedRecords.getInventoryRecords());

    return representation;
  }

  private HttpResult<RequestAndRelatedRecords> setRequestQueuePosition(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    //TODO: Extract to method to add to queue
    requestAndRelatedRecords.withRequest(requestAndRelatedRecords.getRequest()
      .changePosition(requestAndRelatedRecords.getRequestQueue().nextAvailablePosition()));

    return succeeded(requestAndRelatedRecords);
  }

  private HttpResult<RequestAndRelatedRecords> removeRequestQueuePositionWhenCancelled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    //TODO: Extract to cancel method
    if(request.isCancelled()) {
      requestAndRelatedRecords.getRequestQueue().remove(request);
    }

    return succeeded(requestAndRelatedRecords);
  }
}
