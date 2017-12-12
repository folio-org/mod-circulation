package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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

    itemsStorageClient.get(itemId, getItemResponse -> {
      if(getItemResponse.getStatusCode() == 200) {
        JsonObject loadedItem = getItemResponse.getJson();

        if (canCreateRequestForItem(loadedItem, request)) {
          updateItemStatus(itemId, itemStatusFrom(request),
            itemsStorageClient, routingContext.response(), item -> {
              updateLoanActionHistory(itemId,
                loanActionFromRequest(request), itemStatusFrom(request), loansStorageClient,
                routingContext.response(), v -> {
                  addSummariesToRequest(
                    request,
                    itemsStorageClient,
                    holdingsStorageClient,
                    instancesStorageClient,
                    usersStorageClient,
                    requestWithAdditionalInformation -> {
                      requestsStorageClient.post(requestWithAdditionalInformation,
                        requestResponse -> {
                          if (requestResponse.getStatusCode() == 201) {
                            JsonObject createdRequest = requestResponse.getJson();

                            JsonResponse.created(routingContext.response(), createdRequest);
                          } else {
                            ForwardResponse.forward(routingContext.response(), requestResponse);
                          }
                      });
                    },
                    throwable -> {
                      ServerErrorResponse.internalError(routingContext.response(),
                        String.format("At least one request for additional information failed: %s",
                          throwable));
                    });
                });
           });
        }
        else {
          JsonResponse.unprocessableEntity(routingContext.response(),
            String.format("Item is not %s", CHECKED_OUT), "itemId", itemId);
        }
      }
      else if(getItemResponse.getStatusCode() == 404) {
        JsonResponse.unprocessableEntity(routingContext.response(),
          "Item does not exist", "itemId", itemId);
      }
      else {
        ForwardResponse.forward(routingContext.response(), getItemResponse);
      }
    });
  }

  private void replace(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
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

    String id = routingContext.request().getParam("id");
    JsonObject request = routingContext.getBodyAsJson();

    request.remove("item");
    request.remove("requester");

    addSummariesToRequest(request, itemsStorageClient, holdingsStorageClient,
      instancesStorageClient, usersStorageClient,
      requestWithAdditionalInformation -> {
        requestsStorageClient.put(id, requestWithAdditionalInformation, response -> {
          if(response.getStatusCode() == 204) {
            SuccessResponse.noContent(routingContext.response());
          }
          else {
            ForwardResponse.forward(routingContext.response(), response);
          }
        });
      },
      throwable -> {
        ServerErrorResponse.internalError(routingContext.response(),
          String.format("At least one request for additional information failed: %s",
            throwable));
      });
  }

  private void get(RoutingContext routingContext) {
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

    requestsStorageClient.get(id, requestResponse -> {
      if(requestResponse.getStatusCode() == 200) {
        JsonObject request = new JsonObject(requestResponse.getBody());
        JsonResponse.success(routingContext.response(), request);
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

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      requestsStorageClient = createRequestsStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    requestsStorageClient.getMany(routingContext.request().query(), requestsResponse -> {
      if(requestsResponse.getStatusCode() == 200) {
        JsonObject wrappedRequests = new JsonObject(requestsResponse.getBody());

        JsonObject requestsWrapper = new JsonObject()
          .put("requests", wrappedRequests.getJsonArray("requests"))
          .put("totalRecords", wrappedRequests.getInteger("totalRecords"));

        JsonResponse.success(routingContext.response(),
          requestsWrapper);
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

  private void addSummariesToRequest(
    JsonObject request,
    CollectionResourceClient itemsStorageClient,
    CollectionResourceClient holdingsStorageClient,
    CollectionResourceClient instancesStorageClient,
    CollectionResourceClient usersStorageClient,
    Consumer<JsonObject> onSuccess,
    Consumer<Throwable> onFailure) {

    CompletableFuture<Response> itemRequestCompleted = new CompletableFuture<>();
    CompletableFuture<Response> holdingRequestCompleted = new CompletableFuture<>();
    CompletableFuture<Response> instanceRequestCompleted = new CompletableFuture<>();
    CompletableFuture<Response> requestingUserRequestCompleted = new CompletableFuture<>();

    itemsStorageClient.get(request.getString("itemId"),
      itemRequestCompleted::complete);

    itemRequestCompleted.thenAccept(itemResponse -> {
      if(itemResponse != null && itemResponse.getStatusCode() == 200) {

        JsonObject item = itemResponse.getJson();

        holdingsStorageClient.get(item.getString("holdingsRecordId"),
          holdingRequestCompleted::complete);
      }
      else {
        holdingRequestCompleted.complete(null);
      }
    });

    holdingRequestCompleted.thenAccept(holdingResponse -> {
      if(holdingResponse != null && holdingResponse.getStatusCode() == 200) {

        JsonObject holding = holdingResponse.getJson();

        instancesStorageClient.get(holding.getString("instanceId"),
          instanceRequestCompleted::complete);
      }
      else {
        instanceRequestCompleted.complete(null);
      }
    });

    usersStorageClient.get(request.getString("requesterId"),
      requestingUserRequestCompleted::complete);

    CompletableFuture<Void> allCompleted = CompletableFuture.allOf(
      itemRequestCompleted, holdingRequestCompleted, instanceRequestCompleted,
      requestingUserRequestCompleted);

    allCompleted.exceptionally(t -> {
      onFailure.accept(t);
      return null;
    });

    allCompleted.thenAccept(v -> {
      Response itemResponse = itemRequestCompleted.join();
      Response instanceResponse = instanceRequestCompleted.join();
      Response requestingUserResponse = requestingUserRequestCompleted.join();

      JsonObject requestWithAdditionalInformation = request.copy();

      if (itemResponse != null && itemResponse.getStatusCode() == 200) {
        JsonObject item = itemResponse.getJson();

        JsonObject instance = instanceResponse != null
          && instanceResponse.getStatusCode() == 200
          ? instanceResponse.getJson()
          : null;

        String title = instance != null && instance.containsKey("title")
          ? instance.getString("title")
          : item.getString("title");

        JsonObject itemSummary = new JsonObject()
          .put("title", title);

        if(item.containsKey("barcode")) {
          itemSummary.put("barcode", item.getString("barcode"));
        }

        requestWithAdditionalInformation.put("item", itemSummary);
      }

      if (requestingUserResponse.getStatusCode() == 200) {
        JsonObject requester = requestingUserResponse.getJson();

        JsonObject requesterSummary = new JsonObject()
          .put("lastName", requester.getJsonObject("personal").getString("lastName"))
          .put("firstName", requester.getJsonObject("personal").getString("firstName"));

        if(requester.getJsonObject("personal").containsKey("middleName")) {
          requesterSummary.put("middleName",
            requester.getJsonObject("personal").getString("middleName"));
        }

        if(requester.containsKey("barcode")) {
          requesterSummary.put("barcode", requester.getString("barcode"));
        }

        requestWithAdditionalInformation.put("requester", requesterSummary);
      }

      onSuccess.accept(requestWithAdditionalInformation);
    });
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
    WebContext context, String path) throws MalformedURLException {

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl(path));
  }
}
