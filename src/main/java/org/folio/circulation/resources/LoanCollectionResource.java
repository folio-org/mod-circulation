package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.HttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
    WebContext context = new WebContext(routingContext);
    CollectionResourceClient loansStorageClient;
    CollectionResourceClient itemsStorageClient;

    try {
      HttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    JsonObject loan = routingContext.getBodyAsJson();
    String itemId = loan.getString("itemId");

    updateItemWhenLoanChanges(itemId, itemStatusFrom(loan),
      itemsStorageClient, routingContext.response(), item -> {
        loansStorageClient.post(loan, response -> {
          if(response.getStatusCode() == 201) {
            JsonObject createdLoan = response.getJson();

        createdLoan.put("item", new JsonObject()
          .put("title", item.getString("title"))
          .put("barcode", item.getString("barcode")));

        JsonResponse.created(routingContext.response(), createdLoan);
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
      });
    });
  }

  private void replace(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    CollectionResourceClient loansStorageClient;
    CollectionResourceClient itemsStorageClient;

    try {
      HttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    String id = routingContext.request().getParam("id");

    JsonObject loan = routingContext.getBodyAsJson();
    String itemId = loan.getString("itemId");

    updateItemWhenLoanChanges(itemId, itemStatusFrom(loan),
      itemsStorageClient, routingContext.response(), item -> {
        loansStorageClient.put(id, routingContext.getBodyAsJson(), response -> {
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
    CollectionResourceClient loansStorageClient;
    CollectionResourceClient itemsStorageClient;

    try {
      HttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    String id = routingContext.request().getParam("id");

    loansStorageClient.get(id, loanResponse -> {
      if(loanResponse.getStatusCode() == 200) {
        JsonObject loan = new JsonObject(loanResponse.getBody());
        String itemId = loan.getString("itemId");

        itemsStorageClient.get(itemId, itemResponse -> {
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
    WebContext context = new WebContext(routingContext);
    CollectionResourceClient loansStorageClient;

    try {
      HttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    String id = routingContext.request().getParam("id");

    loansStorageClient.delete(id, response -> {
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
    CollectionResourceClient loansStorageClient;
    CollectionResourceClient itemsStorageClient;

    try {
      HttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    loansStorageClient.getMany(routingContext.request().query(), loansResponse -> {
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

          itemsStorageClient.get(loanResource.getString("itemId"),
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
    WebContext context = new WebContext(routingContext);
    CollectionResourceClient loansStorageClient;

    try {
      HttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    loansStorageClient.delete(response -> {
      if(response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
  }

  private HttpClient createHttpClient(RoutingContext routingContext,
                                      WebContext context)
    throws MalformedURLException {

    return new HttpClient(routingContext.vertx(),
      new URL(context.getOkapiLocation()),
      exception -> {
        ServerErrorResponse.internalError(routingContext.response(),
          String.format("Failed to contact storage module: %s",
            exception.toString()));
      });
  }

  private CollectionResourceClient createLoansStorageClient(
    HttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient loanStorageClient;

    loanStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/loan-storage/loans"),
      context.getTenantId());

    return loanStorageClient;
  }

  private CollectionResourceClient createItemsStorageClient(
    HttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient loanStorageClient;

    loanStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/item-storage/items"),
      context.getTenantId());

    return loanStorageClient;
  }

  private void updateItemWhenLoanChanges(
    String itemId,
    String newItemStatus,
    CollectionResourceClient itemsStorageClient,
    HttpServerResponse responseToClient,
    Consumer<JsonObject> onSuccess) {

    itemsStorageClient.get(itemId, getItemResponse -> {
      if(getItemResponse.getStatusCode() == 200) {
        JsonObject item = getItemResponse.getJson();

          if(itemStatusAlreadyMatches(item, newItemStatus)) {
            onSuccess.accept(item);
          }
          else {
            item.put("status", new JsonObject().put("name", newItemStatus));

            itemsStorageClient.put(itemId,
              item, putItemResponse -> {
                if(putItemResponse.getStatusCode() == 204) {
                  onSuccess.accept(item);
                }
                else {
                  ForwardResponse.forward(responseToClient, putItemResponse);
                }
              });
          }
        }
        else if(getItemResponse.getStatusCode() == 404) {
          ServerErrorResponse.internalError(responseToClient,
            "Failed to handle updating an item which does not exist");
        }
        else {
          ForwardResponse.forward(responseToClient, getItemResponse);
        }
      });
  }

  private boolean itemStatusAlreadyMatches(JsonObject item, String newItemStatus) {
    return item.getJsonObject("status").getString("name") == newItemStatus;
  }

  private String itemStatusFrom(JsonObject loan) {
    switch(loan.getJsonObject("status").getString("name")) {
      case "Open":
        return "Checked Out";

      case "Closed":
        return "Available";

      default:
        //TODO: Need to add validation to stop this situation
        return "";
    }
  }
}
