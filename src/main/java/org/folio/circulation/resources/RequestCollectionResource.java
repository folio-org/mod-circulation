package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
    CollectionResourceClient usersStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      requestsStorageClient = createRequestsStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      usersStorageClient = createUsersStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }


    addSummariesToRequest(
      request,
      itemsStorageClient,
      usersStorageClient,
      requestWithAdditionalInformation -> {
        requestsStorageClient.post(requestWithAdditionalInformation, requestResponse -> {
          if (requestResponse.getStatusCode() == 201) {
            JsonObject createdRequest = requestResponse.getJson();

            JsonResponse.created(routingContext.response(), createdRequest);
          } else {
            ForwardResponse.forward(routingContext.response(), requestResponse);
          }
        });
      });
  }

  private void replace(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    CollectionResourceClient requestsStorageClient;
    CollectionResourceClient itemsStorageClient;
    CollectionResourceClient usersStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      requestsStorageClient = createRequestsStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
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

    addSummariesToRequest(request, itemsStorageClient, usersStorageClient,
      requestWithAdditionalInformation -> {
        requestsStorageClient.put(id, requestWithAdditionalInformation, response -> {
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
    CollectionResourceClient usersStorageClient,
    Consumer<JsonObject> onSuccess) {
    CompletableFuture<Response> itemRequestCompleted = new CompletableFuture<>();
    CompletableFuture<Response> requestingUserRequestCompleted = new CompletableFuture<>();

    itemsStorageClient.get(request.getString("itemId"), itemResponse -> {
      itemRequestCompleted.complete(itemResponse);
    });

    usersStorageClient.get(request.getString("requesterId"), userResponse -> {
      requestingUserRequestCompleted.complete(userResponse);
    });

    CompletableFuture.allOf(itemRequestCompleted, requestingUserRequestCompleted)
      .thenAccept(v -> {
        Response itemResponse = itemRequestCompleted.join();
        Response requestingUserResponse = requestingUserRequestCompleted.join();

        JsonObject requestWithAdditionalInformation = request.copy();

        if (itemResponse.getStatusCode() == 200) {
          JsonObject item = itemResponse.getJson();

          JsonObject itemSummary = new JsonObject()
            .put("title", item.getString("title"));

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

    CollectionResourceClient requestStorageClient;

    requestStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/request-storage/requests"),
      context.getTenantId());

    return requestStorageClient;
  }

  private CollectionResourceClient createItemsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient itemsStorageClient;

    itemsStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/item-storage/items"),
      context.getTenantId());

    return itemsStorageClient;
  }

  private CollectionResourceClient createUsersStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient usersStorageClient;

    usersStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/users"),
      context.getTenantId());

    return usersStorageClient;
  }
}
