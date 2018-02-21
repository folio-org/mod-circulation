package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;

import java.net.MalformedURLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatusAssistant.updateItemStatus;
import static org.folio.circulation.domain.LoanActionHistoryAssistant.updateLoanActionHistory;
import static org.folio.circulation.support.CommonFailures.reportFailureToFetchInventoryRecords;
import static org.folio.circulation.support.CommonFailures.reportInvalidOkapiUrlHeader;
import static org.folio.circulation.support.CommonFailures.reportItemRelatedValidationError;
import static org.folio.circulation.support.JsonPropertyCopier.copyStringIfExists;

public class RequestCollectionResource {
  private final String rootPath;

  public RequestCollectionResource(String rootPath) {
    this.rootPath = rootPath;
  }

  public void register(Router router) {
    router.post(rootPath + "*").handler(BodyHandler.create());
    router.put(rootPath + "*").handler(BodyHandler.create());

    router.post(rootPath).handler(this::create);
    router.get(rootPath).handler(this::getMany);
    router.delete(rootPath).handler(this::empty);

    router.route(HttpMethod.GET, rootPath + "/:id").handler(this::get);
    router.route(HttpMethod.PUT, rootPath + "/:id").handler(this::replace);
    router.route(HttpMethod.DELETE, rootPath + "/:id").handler(this::delete);
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
    Clients clients;

    try {
      clients = Clients.create(context);
    }
    catch (MalformedURLException e) {
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

      return;
    }

    String itemId = getItemId(request);

    InventoryFetcher inventoryFetcher = InventoryFetcher.create(clients);

    CompletableFuture<InventoryRecords> inventoryRecordsCompleted
      = inventoryFetcher.fetch(itemId, t ->
      reportFailureToFetchInventoryRecords(routingContext, t));

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

      InventoryRecords inventoryRecords = inventoryRecordsCompleted.join();

      JsonObject item = inventoryRecords.getItem();
      JsonObject holding = inventoryRecords.getHolding();
      JsonObject instance = inventoryRecords.getInstance();

      JsonObject requester = getRecordFromResponse(requestingUserResponse);

      if(item == null) {
        reportItemRelatedValidationError(routingContext, itemId, "Item does not exist");
      }
      else if (RequestType.from(request).canCreateRequestForItem(item)) {
        updateItemStatus(itemId, RequestType.from(request).toItemStatus(),
          clients.itemsStorage(), routingContext.response(), updatedItem ->
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
            }));
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
    Clients clients;

    try {
      clients = Clients.create(context);
    }
    catch (MalformedURLException e) {
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

      return;
    }

    String itemId = getItemId(request);

    InventoryFetcher inventoryFetcher = InventoryFetcher.create(clients);

    CompletableFuture<InventoryRecords> inventoryRecordsCompleted
      = inventoryFetcher.fetch(itemId, t ->
        reportFailureToFetchInventoryRecords(routingContext, t));

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

      InventoryRecords inventoryRecords = inventoryRecordsCompleted.join();

      JsonObject item = inventoryRecords.getItem();
      JsonObject instance = inventoryRecords.getInstance();

      JsonObject requester = getRecordFromResponse(requestingUserResponse);

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
    Clients clients;

    try {
      clients = Clients.create(context);
    }
    catch (MalformedURLException e) {
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

      return;
    }

    String id = routingContext.request().getParam("id");

    clients.requestsStorage().get(id, requestResponse -> {
      if(requestResponse.getStatusCode() == 200) {
        JsonObject request = requestResponse.getJson();

        InventoryFetcher inventoryFetcher = InventoryFetcher.create(clients);

        CompletableFuture<InventoryRecords> inventoryRecordsCompleted =
          inventoryFetcher.fetch(getItemId(request), t ->
            reportFailureToFetchInventoryRecords(routingContext, t));

        inventoryRecordsCompleted.thenAccept(r -> {
          addAdditionalItemProperties(request, r.getHolding(), r.getItem());

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
    Clients clients;

    try {
      clients = Clients.create(context);
    }
    catch (MalformedURLException e) {
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

      return;
    }

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
    Clients clients;

    try {
      clients = Clients.create(context);
    }
    catch (MalformedURLException e) {
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

      return;
    }

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
    Clients clients;

    try {
      clients = Clients.create(context);
    }
    catch (MalformedURLException e) {
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

      return;
    }

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
