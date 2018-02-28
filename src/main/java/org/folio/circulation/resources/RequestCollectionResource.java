package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.LoanActionHistoryAssistant.updateLoanActionHistory;
import static org.folio.circulation.support.CommonFailures.reportItemRelatedValidationError;
import static org.folio.circulation.support.JsonPropertyCopier.copyStringIfExists;

public class RequestCollectionResource {
  private final String rootPath;

  public RequestCollectionResource(String rootPath) {
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
    JsonObject request = routingContext.getBodyAsJson();

    RequestStatus status = RequestStatus.from(request);

    if(!status.isValid()) {
      ClientErrorResponse.badRequest(routingContext.response(),
        RequestStatus.invalidStatusErrorMessage());
      return;
    }
    else {
      status.writeTo(request);
    }

    removeRelatedRecordInformation(request);

    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context);

    String itemId = getItemId(request);

    InventoryFetcher inventoryFetcher = InventoryFetcher.create(clients);

    CompletableFuture<HttpResult<InventoryRecords>> inventoryRecordsCompleted
      = inventoryFetcher.fetch(itemId);

    CompletableFuture<Response> requestingUserRequestCompleted = new CompletableFuture<>();

    clients.usersStorage().get(request.getString("requesterId"),
      requestingUserRequestCompleted::complete);

    CompletableFuture<Void> allCompleted = CompletableFuture.allOf(
      inventoryRecordsCompleted, requestingUserRequestCompleted);

    allCompleted.exceptionally(t -> {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("At least one request for additional information failed: %s", t));

      return null;
    });

    allCompleted.thenAccept(v -> {
      Response requestingUserResponse = requestingUserRequestCompleted.join();

      HttpResult<InventoryRecords> inventoryRecordsResult = inventoryRecordsCompleted.join();

      if(inventoryRecordsResult.failed()) {
        inventoryRecordsResult.cause().writeTo(routingContext.response());
        return;
      }

      JsonObject item = inventoryRecordsResult.value().getItem();
      JsonObject holding = inventoryRecordsResult.value().getHolding();
      JsonObject instance = inventoryRecordsResult.value().getInstance();

      JsonObject requester = getRecordFromResponse(requestingUserResponse);

      if(item == null) {
        reportItemRelatedValidationError(routingContext, itemId, "Item does not exist");
      }
      else if (RequestType.from(request).canCreateRequestForItem(item)) {
        UpdateItem updateItem = new UpdateItem(clients);

        updateItem.onRequestCreation(item, RequestType.from(request).toItemStatus())
          .thenAccept(updateItemResult -> {
            if(updateItemResult.failed()) {
              updateItemResult.cause().writeTo(routingContext.response());
              return;
            }

            updateLoanActionHistory(itemId,
              RequestType.from(request).toLoanAction(), RequestType.from(request).toItemStatus(),
              clients.loansStorage(), routingContext.response(), vo -> {
                addStoredItemProperties(request, item, instance);
                addStoredRequesterProperties(request, requester);

                clients.requestsStorage().post(request, requestResponse -> {
                  if (requestResponse.getStatusCode() == 201) {
                    JsonObject createdRequest = requestResponse.getJson();

                    addAdditionalItemProperties(createdRequest, holding, item);

                    JsonResponse.created(routingContext.response(), createdRequest);
                  } else {
                    ForwardResponse.forward(routingContext.response(), requestResponse);
                  }
                });
              });
          });
      }
      else {
        reportItemRelatedValidationError(routingContext, itemId,
          String.format("Item is not %s", CHECKED_OUT));
      }
    });
  }

  private void replace(RoutingContext routingContext) {
    String id = routingContext.request().getParam("id");
    JsonObject request = routingContext.getBodyAsJson();

    removeRelatedRecordInformation(request);

    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context);

    String itemId = getItemId(request);

    InventoryFetcher inventoryFetcher = InventoryFetcher.create(clients);

    CompletableFuture<HttpResult<InventoryRecords>> inventoryRecordsCompleted
      = inventoryFetcher.fetch(itemId);

    CompletableFuture<Response> requestingUserRequestCompleted = new CompletableFuture<>();

    clients.usersStorage().get(request.getString("requesterId"),
      requestingUserRequestCompleted::complete);

    CompletableFuture<Void> allCompleted = CompletableFuture.allOf(
      inventoryRecordsCompleted, requestingUserRequestCompleted);

    allCompleted.exceptionally(t -> {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("At least one request for additional information failed: %s", t));

      return null;
    });

    allCompleted.thenAccept(v -> {
      Response requestingUserResponse = requestingUserRequestCompleted.join();
      HttpResult<InventoryRecords> inventoryRecordsResult = inventoryRecordsCompleted.join();

      if(inventoryRecordsResult.failed()) {
        inventoryRecordsResult.cause().writeTo(routingContext.response());
        return;
      }

      final InventoryRecords inventoryRecords = inventoryRecordsResult.value();

      final JsonObject item = inventoryRecords.getItem();
      final JsonObject instance = inventoryRecords.getInstance();
      final JsonObject requester = getRecordFromResponse(requestingUserResponse);

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
    Clients clients = Clients.create(context);

    String id = routingContext.request().getParam("id");

    clients.requestsStorage().get(id, requestResponse -> {
      if(requestResponse.getStatusCode() == 200) {
        JsonObject request = requestResponse.getJson();

        InventoryFetcher inventoryFetcher = InventoryFetcher.create(clients);

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
    Clients clients = Clients.create(context);

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
    Clients clients = Clients.create(context);

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

        InventoryFetcher inventoryFetcher = InventoryFetcher.create(clients);

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
    Clients clients = Clients.create(context);

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

  private JsonObject getRecordFromResponse(Response itemResponse) {
    return itemResponse != null && itemResponse.getStatusCode() == 200
      ? itemResponse.getJson()
      : null;
  }

  private void removeRelatedRecordInformation(JsonObject request) {
    request.remove("item");
    request.remove("requester");
  }
}
