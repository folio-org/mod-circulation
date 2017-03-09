package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.circulation.support.http.client.BufferHelper;
import org.folio.circulation.support.http.client.HttpClient;
import org.folio.circulation.support.http.server.*;

import java.net.MalformedURLException;
import java.net.URL;

public class LoanCollectionResource {

  private final String rootPath;

  public LoanCollectionResource(String rootPath) {
    this.rootPath = rootPath;
  }

  public void register(Router router) {
    router.post(rootPath + "*").handler(BodyHandler.create());
    router.put(rootPath + "*").handler(BodyHandler.create());

    router.post(rootPath).handler(this::create);
    router.delete(rootPath).handler(this::empty);

    router.route(HttpMethod.GET, rootPath + "/:id").handler(this::get);
    router.route(HttpMethod.PUT, rootPath + "/:id").handler(this::replace);
  }

  private void create(RoutingContext routingContext) {

    URL okapiLocation;
    URL storageLocation;

    WebContext context = new WebContext(routingContext);

    try {
      okapiLocation = new URL(context.getOkapiLocation());
      storageLocation = context.getOkapiBasedUrl("/loan-storage/loans");
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    HttpClient client = new HttpClient(routingContext.vertx(), okapiLocation,
      exception -> {
        ServerErrorResponse.internalError(routingContext.response(),
          String.format("Failed to contact storage module: %s",
            exception.toString()));
      });

    client.post(storageLocation,
      routingContext.getBodyAsJson(),
      context.getTenantId(), response -> {
        response.bodyHandler(buffer -> {
          String responseBody = BufferHelper.stringFromBuffer(buffer);

          if(response.statusCode() == 201) {
            JsonResponse.created(routingContext.response(),
              new JsonObject(responseBody));
          }
          else {
            ForwardResponse.forward(routingContext.response(), response,
              responseBody);
          }
        });
      });
  }

  private void replace(RoutingContext routingContext) {
    URL okapiLocation;
    URL storageLocation;

    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    try {
      okapiLocation = new URL(context.getOkapiLocation());
      storageLocation = context.getOkapiBasedUrl("/loan-storage/loans");
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    HttpClient client = new HttpClient(routingContext.vertx(), okapiLocation,
      exception -> {
        ServerErrorResponse.internalError(routingContext.response(),
          String.format("Failed to contact storage module: %s",
            exception.toString()));
      });

    JsonObject requestBody = routingContext.getBodyAsJson();

    client.put(storageLocation + String.format("/%s", id),
      requestBody, context.getTenantId(), response -> {
        response.bodyHandler(buffer -> {
          String responseBody = BufferHelper.stringFromBuffer(buffer);

          if(response.statusCode() == 204) {
            SuccessResponse.noContent(routingContext.response());
          }
          else {
            ForwardResponse.forward(routingContext.response(), response,
              responseBody);
          }
        });
      });
  }

  private void get(RoutingContext routingContext) {
    URL okapiLocation;
    URL storageLocation;

    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    try {
      okapiLocation = new URL(context.getOkapiLocation());
      storageLocation = context.getOkapiBasedUrl("/loan-storage/loans");
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    HttpClient client = new HttpClient(routingContext.vertx(), okapiLocation,
      exception -> {
        ServerErrorResponse.internalError(routingContext.response(),
          String.format("Failed to contact storage module: %s",
            exception.toString()));
      });

    client.get(storageLocation + String.format("/%s", id),
      context.getTenantId(), response -> {
        response.bodyHandler(buffer -> {
          String responseBody = BufferHelper.stringFromBuffer(buffer);

          if(response.statusCode() == 200) {
            JsonResponse.success(routingContext.response(),
              new JsonObject(responseBody));
          }
          else {
            ForwardResponse.forward(routingContext.response(), response,
              responseBody);
          }
        });
      });
  }

  private void empty(RoutingContext routingContext) {
    URL okapiLocation;
    URL storageLocation;

    WebContext context = new WebContext(routingContext);

    try {
      okapiLocation = new URL(context.getOkapiLocation());
      storageLocation = context.getOkapiBasedUrl("/loan-storage/loans");
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    HttpClient client = new HttpClient(routingContext.vertx(), okapiLocation,
      exception -> {
        ServerErrorResponse.internalError(routingContext.response(),
          String.format("Failed to contact storage module: %s",
            exception.toString()));
      });

    client.delete(storageLocation, context.getTenantId(), response -> {
          if(response.statusCode() == 204) {
            SuccessResponse.noContent(routingContext.response());
          }
          else {
            response.bodyHandler(buffer -> {
              String responseBody = BufferHelper.stringFromBuffer(buffer);

            ForwardResponse.forward(routingContext.response(), response,
              responseBody);
          });
        }
      });
  }
}
