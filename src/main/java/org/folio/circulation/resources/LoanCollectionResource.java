package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.*;
import org.folio.circulation.support.http.server.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class LoanCollectionResource {

  private final String rootPath;

  public LoanCollectionResource(String rootPath) {
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

    URL okapiLocation;
    URL loanStorageLocation;

    WebContext context = new WebContext(routingContext);

    try {
      okapiLocation = new URL(context.getOkapiLocation());
      loanStorageLocation = context.getOkapiBasedUrl("/loan-storage/loans");
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

    CollectionResourceClient loanStorageClient = new CollectionResourceClient(
      client, loanStorageLocation, context.getTenantId());

    loanStorageClient.post(routingContext.getBodyAsJson(), response -> {
      if(response.getStatusCode() == 201) {
        JsonResponse.created(routingContext.response(),
          new JsonObject(response.getBody()));
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
  }

  private void replace(RoutingContext routingContext) {
    URL okapiLocation;
    URL loanStorageLocation;

    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    try {
      okapiLocation = new URL(context.getOkapiLocation());
      loanStorageLocation = context.getOkapiBasedUrl("/loan-storage/loans");
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

      CollectionResourceClient loanStorageClient = new CollectionResourceClient(
        client, loanStorageLocation, context.getTenantId());

      loanStorageClient.put(id, routingContext.getBodyAsJson(), response -> {
        if(response.getStatusCode() == 204) {
          SuccessResponse.noContent(routingContext.response());
        }
        else {
          ForwardResponse.forward(routingContext.response(), response);
        }
      });
  }

  private void get(RoutingContext routingContext) {
    URL okapiLocation;
    URL loanStorageLocation;
    URL itemStorageLocation;

    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    try {
      okapiLocation = new URL(context.getOkapiLocation());
      loanStorageLocation = context.getOkapiBasedUrl("/loan-storage/loans");
      itemStorageLocation = context.getOkapiBasedUrl("/item-storage/items");

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

    CollectionResourceClient loanStorageClient = new CollectionResourceClient(
      client, loanStorageLocation, context.getTenantId());

    CollectionResourceClient itemStorageClient = new CollectionResourceClient(
      client, itemStorageLocation, context.getTenantId());

    loanStorageClient.get(id, loanResponse -> {
      if(loanResponse.getStatusCode() == 200) {
        JsonObject loan = new JsonObject(loanResponse.getBody());
        String itemId = loan.getString("itemId");

        itemStorageClient.get(itemId, itemResponse -> {
          if(itemResponse.getStatusCode() == 200) {
            JsonObject item = new JsonObject(itemResponse.getBody());

            loan.put("item", new JsonObject()
              .put("title", item.getString("title"))
              .put("barcode", item.getString("barcode")));

            JsonResponse.success(routingContext.response(),
              loan);
          }
          else if(itemResponse.getStatusCode() == 404) {
            JsonResponse.success(routingContext.response(),
              loan);
          }
          else {
            ServerErrorResponse.internalError(routingContext.response(),
              String.format("Failed to item with ID: %s:, %s",
                itemId, itemResponse.getBody()));
          }
        });
      }
      else {
        ForwardResponse.forward(routingContext.response(), loanResponse);
      }
    });
  }

  private void delete(RoutingContext routingContext) {
    URL okapiLocation;
    URL loanStorageLocation;

    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    try {
      okapiLocation = new URL(context.getOkapiLocation());
      loanStorageLocation = context.getOkapiBasedUrl("/loan-storage/loans");
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

    CollectionResourceClient loanStorageClient = new CollectionResourceClient(
      client, loanStorageLocation, context.getTenantId());

    loanStorageClient.delete(id, response -> {
      if(response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
  }

  private void getMany(RoutingContext routingContext) {
    URL okapiLocation;
    URL loanStorageLocation;
    URL itemStorageLocation;

    WebContext context = new WebContext(routingContext);

    try {
      okapiLocation = new URL(context.getOkapiLocation());
      loanStorageLocation = context.getOkapiBasedUrl("/loan-storage/loans");
      itemStorageLocation = context.getOkapiBasedUrl("/item-storage/items");
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

    CollectionResourceClient loanStorageClient = new CollectionResourceClient(
      client, loanStorageLocation, context.getTenantId());

    CollectionResourceClient itemStorageClient = new CollectionResourceClient(
      client, itemStorageLocation, context.getTenantId());

    loanStorageClient.getMany(routingContext.request().query(), loansResponse -> {
      if(loansResponse.getStatusCode() == 200) {
        JsonObject wrappedLoans = new JsonObject(loansResponse.getBody());

        List<JsonObject> newLoans = JsonArrayHelper.toList(
          wrappedLoans.getJsonArray("loans"));

        List<CompletableFuture<Response>>
          allFutures = new ArrayList<>();

        newLoans.forEach(loanResource -> {
          CompletableFuture<Response> newFuture
            = new CompletableFuture<>();

          allFutures.add(newFuture);

          itemStorageClient.get(loanResource.getString("itemId"),
            response -> newFuture.complete(response));
        });

        CompletableFuture<Void> allDoneFuture =
          CompletableFuture.allOf(allFutures.toArray(new CompletableFuture<?>[] { }));

        allDoneFuture.thenAccept(v -> {
          List<Response> itemResponses = allFutures.stream().
            map(future -> future.join()).
            collect(Collectors.toList());

          newLoans.forEach( loan -> {
            Optional<JsonObject> possibleItem = itemResponses.stream()
              .filter(itemResponse -> itemResponse.getStatusCode() == 200)
              .map(itemResponse -> itemResponse.getJson())
              .filter(item -> item.getString("id").equals(loan.getString("itemId")))
              .findFirst();

            if(possibleItem.isPresent()) {
              loan.put("item", new JsonObject()
                .put("title", possibleItem.get().getString("title"))
                .put("barcode", possibleItem.get().getString("barcode")));
            }
          });

          JsonObject loansWrapper = new JsonObject()
            .put("loans", new JsonArray(newLoans))
            .put("totalRecords", wrappedLoans.getInteger("totalRecords"));

          JsonResponse.success(routingContext.response(),
            loansWrapper);
        });
      }
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
