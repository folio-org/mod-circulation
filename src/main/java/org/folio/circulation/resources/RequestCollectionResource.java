package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.server.*;

import java.net.MalformedURLException;
import java.net.URL;

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

    JsonObject request = routingContext.getBodyAsJson();

    requestsStorageClient.post(request, response -> {
      if(response.getStatusCode() == 201) {
        JsonObject createdRequest = response.getJson();

        JsonResponse.created(routingContext.response(), createdRequest);
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
  }

  private void replace(RoutingContext routingContext) {
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
    JsonObject request = routingContext.getBodyAsJson();

    requestsStorageClient.put(id, request, response -> {
      if(response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
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
}
