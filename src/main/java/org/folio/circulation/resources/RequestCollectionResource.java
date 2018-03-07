package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.*;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.*;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.*;
import static org.folio.circulation.support.JsonPropertyCopier.copyStringIfExists;

public class RequestCollectionResource extends CollectionResource {
  private final String rootPath;

  public RequestCollectionResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);

    routeRegistration.create(this::create);
    routeRegistration.get(this::get);
    routeRegistration.getMany(this::getMany);
    routeRegistration.replace(this::replace);
    routeRegistration.delete(this::delete);
    routeRegistration.deleteAll(this::empty);
  }

  private void create(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final UserFetcher userFetcher = new UserFetcher(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateLoanActionHistory updateLoanActionHistory = new UpdateLoanActionHistory(clients);

    JsonObject request = routingContext.getBodyAsJson();

    RequestStatus status = RequestStatus.from(request);

    HttpServerResponse response = routingContext.response();
    if(!status.isValid()) {
      ClientErrorResponse.badRequest(response,
        RequestStatus.invalidStatusErrorMessage());
      return;
    }
    else {
      status.writeTo(request);
    }

    removeRelatedRecordInformation(request);

    final String itemId = getItemId(request);
    final String requestingUserId = request.getString("requesterId");

    completedFuture(HttpResult.success(new RequestAndRelatedRecords(request)))
      .thenCombineAsync(inventoryFetcher.fetch(request), this::addInventoryRecords)
      .thenApply(this::refuseWhenItemDoesNotExist)
      .thenApply(this::refuseWhenItemIsNotValid)
      .thenCombineAsync(requestQueueFetcher.get(itemId), this::addRequestQueue)
      .thenCombineAsync(userFetcher.getUser(requestingUserId, false), this::addUser)
      .thenComposeAsync(r -> r.after(updateItem::onRequestCreation))
      .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestCreation))
      .thenComposeAsync(r -> r.after(records -> createRequest(records, clients)))
      .thenApply(r -> r.map(this::extendedRequest))
      .thenApply(CreatedJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private void replace(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);
    final UserFetcher userFetcher = new UserFetcher(clients);

    String id = routingContext.request().getParam("id");
    JsonObject request = routingContext.getBodyAsJson();

    removeRelatedRecordInformation(request);

    final String requestingUserId = request.getString("requesterId");

    completedFuture(HttpResult.success(new RequestAndRelatedRecords(request)))
      .thenCombineAsync(inventoryFetcher.fetch(request), this::addInventoryRecords)
      .thenCombineAsync(userFetcher.getUser(requestingUserId, false), this::addUser)
      .thenAcceptAsync(result -> {
        if(result.failed()) {
          result.cause().writeTo(routingContext.response());
          return;
        }

        final InventoryRecords inventoryRecords = result.value().inventoryRecords;
        final JsonObject item = inventoryRecords.getItem();
        final JsonObject instance = inventoryRecords.getInstance();
        final JsonObject requester = result.value().requestingUser;

        addStoredItemProperties(request, item, instance);
        addStoredRequesterProperties(request, requester);

        clients.requestsStorage().put(id, request, response -> {
          if(response.getStatusCode() == 204) {
            SuccessResponse.noContent(routingContext.response());
          }
          else {
            ForwardResponse.forward(routingContext.response(), response);
          }
        });
      });
  }

  private void get(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    String id = routingContext.request().getParam("id");

    clients.requestsStorage().get(id, requestResponse -> {
      if(requestResponse.getStatusCode() == 200) {
        JsonObject request = requestResponse.getJson();

        InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);

        CompletableFuture<HttpResult<InventoryRecords>> inventoryRecordsCompleted =
          inventoryFetcher.fetch(getItemId(request));

        inventoryRecordsCompleted.thenAccept(r -> {
          if(r.failed()) {
            r.cause().writeTo(routingContext.response());
            return;
          }

          addAdditionalItemProperties(request, r.value().getHolding(),
            r.value().getItem());

          JsonResponse.success(routingContext.response(), request);
        });
      }
      else {
        ForwardResponse.forward(routingContext.response(), requestResponse);
      }
    });
  }

  private void delete(RoutingContext routingContext) {
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

  private void getMany(RoutingContext routingContext) {

    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.requestsStorage().getMany(routingContext.request().query(),
      requestsResponse -> {

      if(requestsResponse.getStatusCode() == 200) {
        final MultipleRecordsWrapper wrappedRequests = MultipleRecordsWrapper.fromRequestBody(
          requestsResponse.getBody(), "requests");

        if(wrappedRequests.isEmpty()) {
          JsonResponse.success(routingContext.response(),
            wrappedRequests.toJson());

          return;
        }

        final Collection<JsonObject> requests = wrappedRequests.getRecords();

        List<String> itemIds = requests.stream()
          .map(this::getItemId)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

        InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);

        CompletableFuture<MultipleInventoryRecords> inventoryRecordsFetched =
          inventoryFetcher.fetch(itemIds, e ->
            ServerErrorResponse.internalError(routingContext.response(), e.toString()));

        inventoryRecordsFetched.thenAccept(records -> {
          requests.forEach( request -> {
              Optional<JsonObject> possibleItem = records.findItemById(getItemId(request));

              if(possibleItem.isPresent()) {
                JsonObject item = possibleItem.get();

                Optional<JsonObject> possibleHolding = records.findHoldingById(
                  item.getString("holdingsRecordId"));

                addAdditionalItemProperties(request,
                  possibleHolding.orElse(null),
                  possibleItem.orElse(null));
              }
            });

            JsonResponse.success(routingContext.response(),
              wrappedRequests.toJson());
        });
      }
    });
  }

  private void empty(RoutingContext routingContext) {
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

  private String getItemId(JsonObject request) {
    return request.getString("itemId");
  }

  private void addStoredItemProperties(
    JsonObject request,
    JsonObject item,
    JsonObject instance) {

    if(item == null) {
      return;
    }

    JsonObject itemSummary = new JsonObject();

    final String titleProperty = "title";

    if(instance != null && instance.containsKey(titleProperty)) {
      itemSummary.put(titleProperty, instance.getString(titleProperty));
    } else copyStringIfExists(titleProperty, item, itemSummary);

    copyStringIfExists("barcode", item, itemSummary);

    request.put("item", itemSummary);
  }

  private void addAdditionalItemProperties(
    JsonObject request,
    JsonObject holding,
    JsonObject item) {

    if(item == null)
      return;

    JsonObject itemSummary = request.containsKey("item")
      ? request.getJsonObject("item")
      : new JsonObject();

    copyStringIfExists("holdingsRecordId", item, itemSummary);

    if(holding != null) {
      copyStringIfExists("instanceId", holding, itemSummary);
    }

    request.put("item", itemSummary);
  }

  private void addStoredRequesterProperties
    (JsonObject requestWithAdditionalInformation,
     JsonObject requester) {

    if(requester == null) {
      return;
    }

    JsonObject requesterSummary = new JsonObject();

    if(requester.containsKey("personal")) {
      JsonObject personalDetails = requester.getJsonObject("personal");

      copyStringIfExists("lastName", personalDetails, requesterSummary);
      copyStringIfExists("firstName", personalDetails, requesterSummary);
      copyStringIfExists("middleName", personalDetails, requesterSummary);
    }

    copyStringIfExists("barcode", requester, requesterSummary);

    requestWithAdditionalInformation.put("requester", requesterSummary);
  }

  private void removeRelatedRecordInformation(JsonObject request) {
    request.remove("item");
    request.remove("requester");
  }

  private HttpResult<RequestAndRelatedRecords> addInventoryRecords(
    HttpResult<RequestAndRelatedRecords> loanResult,
    HttpResult<InventoryRecords> inventoryRecordsResult) {

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
    HttpResult<JsonObject> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      RequestAndRelatedRecords::withRequestingUser);
  }

  private HttpResult<RequestAndRelatedRecords> refuseWhenItemDoesNotExist(
    HttpResult<RequestAndRelatedRecords> result) {

    return result.next(requestAndRelatedRecords -> {
      if(requestAndRelatedRecords.inventoryRecords.getItem() == null) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Item does not exist", "itemId",
          requestAndRelatedRecords.request.getString("itemId")));
      }
      else {
        return result;
      }
    });
  }

  private HttpResult<RequestAndRelatedRecords> refuseWhenItemIsNotValid(
    HttpResult<RequestAndRelatedRecords> result) {

    return result.next(requestAndRelatedRecords -> {
      JsonObject request = requestAndRelatedRecords.request;
      JsonObject item = requestAndRelatedRecords.inventoryRecords.item;

      RequestType requestType = RequestType.from(request);

      if (!requestType.canCreateRequestForItem(item)) {
        return HttpResult.failure(new ValidationErrorFailure(
          String.format("Item is not %s, %s or %s", CHECKED_OUT,
            CHECKED_OUT_HELD, CHECKED_OUT_RECALLED),
          "itemId", request.getString("itemId")
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

    JsonObject request = requestAndRelatedRecords.request;

    JsonObject item = requestAndRelatedRecords.inventoryRecords.getItem();
    JsonObject instance = requestAndRelatedRecords.inventoryRecords.getInstance();
    JsonObject requestingUser = requestAndRelatedRecords.requestingUser;

    addStoredItemProperties(request, item, instance);
    addStoredRequesterProperties(request, requestingUser);

    clients.requestsStorage().post(request, response -> {
      if (response.getStatusCode() == 201) {
        onCreated.complete(HttpResult.success(
          requestAndRelatedRecords.withRequest(response.getJson())));
      } else {
        onCreated.complete(HttpResult.failure(new ForwardOnFailure(response)));
      }
    });

    return onCreated;
  }

  private JsonObject extendedRequest(RequestAndRelatedRecords requestAndRelatedRecords) {
    JsonObject item = requestAndRelatedRecords.inventoryRecords.getItem();
    JsonObject holding = requestAndRelatedRecords.inventoryRecords.getHolding();

    addAdditionalItemProperties(requestAndRelatedRecords.request, holding, item);

    return requestAndRelatedRecords.request;
  }
}
