package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.folio.circulation.domain.ItemStatus.*;
import static org.folio.circulation.domain.ItemStatusAssistant.updateItemStatus;
import static org.folio.circulation.domain.LoanActionHistoryAssistant.updateLoanActionHistory;

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
    WebContext context = new WebContext(routingContext);

    JsonObject request = routingContext.getBodyAsJson();

    request.remove("item");
    request.remove("requester");

    CollectionResourceClient requestsStorageClient;
    CollectionResourceClient itemsStorageClient;
    CollectionResourceClient holdingsStorageClient;
    CollectionResourceClient instancesStorageClient;
    CollectionResourceClient usersStorageClient;
    CollectionResourceClient loansStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      requestsStorageClient = createRequestsStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      instancesStorageClient = createInstanceStorageClient(client, context);
      usersStorageClient = createUsersStorageClient(client, context);
      loansStorageClient = createLoansStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    String itemId = request.getString("itemId");

    InventoryFetcher inventoryFetcher = new InventoryFetcher(
      itemsStorageClient, holdingsStorageClient, instancesStorageClient);

    CompletableFuture<InventoryRecords> inventoryRecordsCompleted
      = inventoryFetcher.fetch(itemId, t -> {
          ServerErrorResponse.internalError(routingContext.response(),
            String.format(
              "Could not get inventory records related to request: %s", t));
    });

    CompletableFuture<Response> requestingUserRequestCompleted = new CompletableFuture<>();

    usersStorageClient.get(request.getString("requesterId"),
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
        JsonResponse.unprocessableEntity(routingContext.response(),
          "Item does not exist", "itemId", itemId);
      }
      else if (canCreateRequestForItem(item, request)) {
        updateItemStatus(itemId, itemStatusFrom(request),
          itemsStorageClient, routingContext.response(), updatedItem -> {
            updateLoanActionHistory(itemId,
              loanActionFromRequest(request), itemStatusFrom(request), loansStorageClient,
              routingContext.response(), vo -> {
                addStoredItemProperties(request, item, instance);
                addStoredRequesterProperties(request, requester);

                requestsStorageClient.post(request, requestResponse -> {
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
        JsonResponse.unprocessableEntity(routingContext.response(),
          String.format("Item is not %s", CHECKED_OUT), "itemId", itemId);
      }
    });
  }

  private void replace(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");
    JsonObject request = routingContext.getBodyAsJson();

    request.remove("item");
    request.remove("requester");

    CollectionResourceClient requestsStorageClient;
    CollectionResourceClient itemsStorageClient;
    CollectionResourceClient holdingsStorageClient;
    CollectionResourceClient instancesStorageClient;
    CollectionResourceClient usersStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      requestsStorageClient = createRequestsStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      instancesStorageClient = createInstanceStorageClient(client, context);
      usersStorageClient = createUsersStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    String itemId = request.getString("itemId");

    InventoryFetcher inventoryFetcher = new InventoryFetcher(
      itemsStorageClient, holdingsStorageClient, instancesStorageClient);

    CompletableFuture<InventoryRecords> inventoryRecordsCompleted
      = inventoryFetcher.fetch(itemId, t -> {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format(
          "Could not get inventory records related to request: %s", t));
    });

    CompletableFuture<Response> requestingUserRequestCompleted = new CompletableFuture<>();

    usersStorageClient.get(request.getString("requesterId"),
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

      requestsStorageClient.put(id, request, response -> {
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

    CollectionResourceClient requestsStorageClient;
    CollectionResourceClient itemsStorageClient;
    CollectionResourceClient holdingsStorageClient;
    CollectionResourceClient instancesStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      requestsStorageClient = createRequestsStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      instancesStorageClient = createInstanceStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    String id = routingContext.request().getParam("id");

    requestsStorageClient.get(id, requestResponse -> {
      if(requestResponse.getStatusCode() == 200) {
        InventoryFetcher fetcher = new InventoryFetcher(itemsStorageClient,
          holdingsStorageClient, instancesStorageClient);

        JsonObject request = requestResponse.getJson();

        CompletableFuture<InventoryRecords> inventoryRecordsCompleted =
          fetcher.fetch(request.getString("itemId"), t -> {
          ServerErrorResponse.internalError(routingContext.response(),
            String.format(
              "Could not get inventory records related to request: %s", t));
        });

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
    CollectionResourceClient requestsStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      requestsStorageClient = createRequestsStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    String id = routingContext.request().getParam("id");

    requestsStorageClient.delete(id, response -> {
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

    CollectionResourceClient requestsStorageClient;
    CollectionResourceClient itemsStorageClient;
    CollectionResourceClient holdingsStorageClient;
    CollectionResourceClient instancesStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      requestsStorageClient = createRequestsStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      instancesStorageClient = createInstanceStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    requestsStorageClient.getMany(routingContext.request().query(), requestsResponse -> {
      if(requestsResponse.getStatusCode() == 200) {
        JsonObject wrappedRequests = new JsonObject(requestsResponse.getBody());

        final List<JsonObject> requests = JsonArrayHelper.toList(
          wrappedRequests.getJsonArray("requests"));

        if(requests.isEmpty()) {
          JsonObject requestsWrapper = new JsonObject()
            .put("requests", new JsonArray(requests))
            .put("totalRecords", wrappedRequests.getInteger("totalRecords"));

          JsonResponse.success(routingContext.response(),
            requestsWrapper);

          return;
        }

        List<String> itemIds = requests.stream()
          .map(request -> request.getString("itemId"))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

        InventoryFetcher inventoryFetcher = new InventoryFetcher(itemsStorageClient,
          holdingsStorageClient, instancesStorageClient);

        CompletableFuture<MultipleInventoryRecords> inventoryRecordsFetched =
          inventoryFetcher.fetch(itemIds, e -> {
            ServerErrorResponse.internalError(routingContext.response(), e.toString());
        });

        inventoryRecordsFetched.thenAccept(records -> {
          requests.forEach( request -> {
              Optional<JsonObject> possibleItem = records.findItemById(
                request.getString("itemId"));

              if(possibleItem.isPresent()) {
                JsonObject item = possibleItem.get();

                Optional<JsonObject> possibleHolding = records.findHoldingById(
                  item.getString("holdingsRecordId"));

                addAdditionalItemProperties(request,
                  possibleHolding.orElse(null),
                  possibleItem.orElse(null));
              }
            });

            JsonObject requestsWrapper = new JsonObject()
              .put("requests", new JsonArray(requests))
              .put("totalRecords", wrappedRequests.getInteger("totalRecords"));

            JsonResponse.success(routingContext.response(),
              requestsWrapper);
        });
      }
    });
  }

  private void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    CollectionResourceClient requestsStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      requestsStorageClient = createRequestsStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    requestsStorageClient.delete(response -> {
      if(response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
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
    } else if (item.containsKey("title")) {
      itemSummary.put(titleProperty, item.getString(titleProperty));
    }

    if(item.containsKey("barcode")) {
      itemSummary.put("barcode", item.getString("barcode"));
    }

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

    itemSummary.put("holdingsRecordId", item.getString("holdingsRecordId"));

    if(holding != null) {
      itemSummary.put("instanceId", holding.getString("instanceId"));
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
      requesterSummary.put("lastName", requester.getJsonObject("personal").getString("lastName"));
      requesterSummary.put("firstName", requester.getJsonObject("personal").getString("firstName"));

      if(requester.getJsonObject("personal").containsKey("middleName")) {
        requesterSummary.put("middleName",
          requester.getJsonObject("personal").getString("middleName"));
      }
    }

    if(requester.containsKey("barcode")) {
      requesterSummary.put("barcode", requester.getString("barcode"));
    }

    requestWithAdditionalInformation.put("requester", requesterSummary);
  }

  private OkapiHttpClient createHttpClient(RoutingContext routingContext,
                                           WebContext context)
    throws MalformedURLException {

    return new OkapiHttpClient(routingContext.vertx().createHttpClient(),
      new URL(context.getOkapiLocation()), context.getTenantId(),
      context.getOkapiToken(),
      exception -> ServerErrorResponse.internalError(routingContext.response(),
        String.format("Failed to contact storage module: %s",
          exception.toString())));
  }

  private CollectionResourceClient createRequestsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/request-storage/requests");
  }

  private CollectionResourceClient createItemsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/item-storage/items");
  }

  private CollectionResourceClient createHoldingsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/holdings-storage/holdings"));
  }

  private CollectionResourceClient createInstanceStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/instance-storage/instances"));
  }

  private CollectionResourceClient createUsersStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/users");
  }

  private CollectionResourceClient createLoansStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/loan-storage/loans");
  }

  private String itemStatusFrom(JsonObject request) {
    switch(request.getString("requestType")) {
      case RequestType.HOLD:
        return CHECKED_OUT_HELD;

      case RequestType.RECALL:
        return CHECKED_OUT_RECALLED;

      case RequestType.PAGE:
        return CHECKED_OUT;

      default:
        //TODO: Need to add validation to stop this situation
        return "";
    }
  }

  private boolean canCreateRequestForItem(JsonObject item, JsonObject request) {
    String status = item.getJsonObject("status").getString("name");

    switch (request.getString("requestType")) {
      case RequestType.HOLD:
      case RequestType.RECALL:
        return StringUtils.equalsIgnoreCase(status, CHECKED_OUT) ||
          StringUtils.equalsIgnoreCase(status, CHECKED_OUT_HELD) ||
            StringUtils.equalsIgnoreCase(status, CHECKED_OUT_RECALLED);

      case RequestType.PAGE:
      default:
        return true;
    }
  }

  private String loanActionFromRequest(JsonObject request) {
    switch (request.getString("requestType")) {
      case RequestType.HOLD:
        return "holdrequested";
      case RequestType.RECALL:
        return "recallrequested";

      case RequestType.PAGE:
      default:
        return null;
    }
  }

  private CollectionResourceClient getCollectionResourceClient(
    OkapiHttpClient client,
    WebContext context,
    String path)
    throws MalformedURLException {

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl(path));
  }

  private JsonObject getRecordFromResponse(Response itemResponse) {
    return itemResponse != null && itemResponse.getStatusCode() == 200
      ? itemResponse.getJson()
      : null;
  }
}
