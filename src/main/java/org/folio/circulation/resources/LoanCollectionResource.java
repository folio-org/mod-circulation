package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatusAssistant.updateItemStatus;

public class LoanCollectionResource {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
    CollectionResourceClient holdingsStorageClient;
    CollectionResourceClient locationsStorageClient;
    CollectionResourceClient instancesStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      locationsStorageClient = createLocationsStorageClient(client, context);
      instancesStorageClient = createInstanceStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    JsonObject loan = routingContext.getBodyAsJson();
    String itemId = loan.getString("itemId");

    updateItemStatus(itemId, itemStatusFrom(loan),
      itemsStorageClient, routingContext.response(), item -> {
        loan.put("itemStatus", item.getJsonObject("status").getString("name"));
        loansStorageClient.post(loan, response -> {
          if(response.getStatusCode() == 201) {
            JsonObject createdLoan = response.getJson();

            String holdingId = item.getString("holdingsRecordId");

            holdingsStorageClient.get(holdingId, holdingResponse -> {

              final String instanceId = holdingResponse.getStatusCode() == 200
                ? holdingResponse.getJson().getString("instanceId")
                : null;

              instancesStorageClient.get(instanceId, instanceResponse -> {
                final JsonObject instance = instanceResponse.getStatusCode() == 200
                  ? instanceResponse.getJson()
                  : null;

                if(item.containsKey("temporaryLocationId")) {
                  String locationId = item.getString("temporaryLocationId");
                  locationsStorageClient.get(locationId,
                    locationResponse -> {
                      if (locationResponse.getStatusCode() == 200) {
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item, instance, locationResponse.getJson()));
                      } else {
                        log.warn(
                          String.format("Could not get location %s for item %s",
                            locationId, itemId));
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item, instance, null));
                      }
                    });
                } else if(holdingResponse.getStatusCode() == 200
                  && holdingResponse.getJson().containsKey("permanentLocationId")) {

                  String locationId = holdingResponse.getJson().getString("permanentLocationId");

                  locationsStorageClient.get(locationId,
                    locationResponse -> {
                      if(locationResponse.getStatusCode() == 200) {
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item,
                            instance, locationResponse.getJson()));
                      }
                      else {
                        log.warn(
                          String.format(
                            "Could not get location %s for item %s from holding %s",
                            locationId, itemId, holdingId));
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item, instance, null));
                      }
                    });
                } else if(item.containsKey("permanentLocationId")) {
                  String locationId = item.getString("permanentLocationId");
                  locationsStorageClient.get(locationId,
                    locationResponse -> {
                      if(locationResponse.getStatusCode() == 200) {
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item, instance,
                            locationResponse.getJson()));
                      }
                      else {
                        log.warn(
                          String.format("Could not get location %s for item %s",
                            locationId, itemId ));
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item, instance, null));
                      }
                  });
                }
                else {
                  JsonResponse.created(routingContext.response(),
                    extendedLoan(createdLoan, item, instance, null));
                }
              });
            });
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
      OkapiHttpClient client = createHttpClient(routingContext, context);
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

    //TODO: Either converge the schema (based upon conversations about sharing
    // schema and including referenced resources or switch to include properties
    // rather than exclude properties
    JsonObject storageLoan = loan.copy();
    storageLoan.remove("item");
    storageLoan.remove("itemStatus");

    updateItemStatus(itemId, itemStatusFrom(loan),
      itemsStorageClient, routingContext.response(), item -> {
        storageLoan.put("itemStatus", item.getJsonObject("status").getString("name"));
        loansStorageClient.put(id, storageLoan, response -> {
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
    CollectionResourceClient holdingsStorageClient;
    CollectionResourceClient locationsStorageClient;
    CollectionResourceClient instancesStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      locationsStorageClient = createLocationsStorageClient(client, context);
      instancesStorageClient = createInstanceStorageClient(client, context);
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

            String holdingId = item.getString("holdingsRecordId");

            holdingsStorageClient.get(holdingId, holdingResponse -> {
              final String instanceId = holdingResponse.getStatusCode() == 200
                ? holdingResponse.getJson().getString("instanceId")
                : null;

              instancesStorageClient.get(instanceId, instanceResponse -> {
                final JsonObject instance = instanceResponse.getStatusCode() == 200
                  ? instanceResponse.getJson()
                  : null;

                if(item.containsKey("temporaryLocationId")) {
                  String locationId = item.getString("temporaryLocationId");
                  locationsStorageClient.get(locationId,
                    locationResponse -> {
                      if(locationResponse.getStatusCode() == 200) {
                        JsonResponse.success(routingContext.response(),
                          extendedLoan(loan, item, instance,
                            locationResponse.getJson()));
                      }
                      else {
                        log.warn(
                          String.format("Could not get location %s for item %s",
                            locationId, itemId ));

                        JsonResponse.success(routingContext.response(),
                          extendedLoan(loan, item, instance, null));
                      }
                    });
                }
                else if(holdingResponse.getStatusCode() == 200
                  && holdingResponse.getJson().containsKey("permanentLocationId")) {

                  String locationId = holdingResponse.getJson().getString("permanentLocationId");

                  locationsStorageClient.get(locationId,
                    locationResponse -> {
                      if(locationResponse.getStatusCode() == 200) {
                        JsonResponse.success(routingContext.response(),
                          extendedLoan(loan, item, instance,
                            locationResponse.getJson()));
                      }
                      else {
                        log.warn(
                          String.format("Could not get location %s for item %s",
                            locationId, itemId ));

                        JsonResponse.success(routingContext.response(),
                          extendedLoan(loan, item, instance, null));
                      }
                    });
                }
                else if (item.containsKey("permanentLocationId")) {
                  String locationId = item.getString("permanentLocationId");
                  locationsStorageClient.get(locationId,
                    locationResponse -> {
                      if(locationResponse.getStatusCode() == 200) {
                        JsonResponse.success(routingContext.response(),
                          extendedLoan(loan, item, instance,
                            locationResponse.getJson()));
                      }
                      else {
                        log.warn(
                          String.format("Could not get location %s for item %s",
                            locationId, itemId ));

                        JsonResponse.success(routingContext.response(),
                          extendedLoan(loan, item, instance, null));
                      }
                    });
                }
                else {
                  JsonResponse.success(routingContext.response(),
                    extendedLoan(loan, item, null, null));
                }
              });


            });
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
      OkapiHttpClient client = createHttpClient(routingContext, context);
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
    CollectionResourceClient locationsClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      locationsClient = createLocationsStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));

      return;
    }

    loansStorageClient.getMany(routingContext.request().query(), loansResponse -> {
      if(loansResponse.getStatusCode() == 200) {
        JsonObject wrappedLoans = new JsonObject(loansResponse.getBody());

        List<JsonObject> loans = JsonArrayHelper.toList(
          wrappedLoans.getJsonArray("loans"));

        List<String> itemIds = loans.stream()
          .map(loan -> loan.getString("itemId"))
          .collect(Collectors.toList());

        CompletableFuture<Response> itemsFetched = new CompletableFuture<>();

        String itemsQuery = multipleRecordsCqlQuery(itemIds);

        itemsStorageClient.getMany(itemsQuery, itemIds.size(), 0,
          itemsFetched::complete);

        itemsFetched.thenAccept(itemsResponse -> {
          if(itemsResponse.getStatusCode() != 200) {
            ServerErrorResponse.internalError(routingContext.response(),
              String.format("Items request (%s) failed %s: %s",
                itemsQuery, itemsResponse.getStatusCode(), itemsResponse.getBody()));
          }

          List<String> locationIds = new ArrayList<>();

          List<JsonObject> items = JsonArrayHelper.toList(
            itemsResponse.getJson().getJsonArray("items"));

          items.stream().forEach(item -> {
              if(item.containsKey("temporaryLocationId")) {
                locationIds.add(item.getString("temporaryLocationId"));
              }
              else if(item.containsKey("permanentLocationId")) {
                locationIds.add(item.getString("permanentLocationId"));
              }
          });

          CompletableFuture<Response> locationsFetched =
            new CompletableFuture<>();

          String query = multipleRecordsCqlQuery(locationIds);

          locationsClient.getMany(query, locationIds.size(), 0,
            locationsFetched::complete);

          locationsFetched.thenAccept(locationsResponse -> {
            if(locationsResponse.getStatusCode() != 200) {
              ServerErrorResponse.internalError(routingContext.response(),
                String.format("Locations request (%s) failed %s: %s",
                  query, locationsResponse.getStatusCode(), locationsResponse.getBody()));
            }

            loans.forEach( loan -> {
              Optional<JsonObject> possibleItem = items.stream()
                .filter(item -> item.getString("id").equals(loan.getString("itemId")))
                .findFirst();

              //No need to pass on the itemStatus property, as only used to populate the history
              //and could be confused with aggregation of current status
              loan.remove("itemStatus");

              if(possibleItem.isPresent()) {
                JsonObject item = possibleItem.get();

                List<JsonObject> locations = JsonArrayHelper.toList(locationsResponse
                  .getJson().getJsonArray("shelflocations"));

                Optional<JsonObject> possiblePermanentLocation = locations.stream()
                  .filter(location -> location.getString("id").equals(
                    item.getString("permanentLocationId")))
                  .findFirst();

                Optional<JsonObject> possibleTemporaryLocation = locations.stream()
                  .filter(location -> location.getString("id").equals(
                    item.getString("temporaryLocationId")))
                  .findFirst();

                loan.put("item", createItemSummary(item, null,
                  possibleTemporaryLocation.orElse(possiblePermanentLocation.orElse(null))));
              }
            });

            JsonObject loansWrapper = new JsonObject()
              .put("loans", new JsonArray(loans))
              .put("totalRecords", wrappedLoans.getInteger("totalRecords"));

            JsonResponse.success(routingContext.response(),
              loansWrapper);
          });
        });
      }
    });
  }

  private void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    CollectionResourceClient loansStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
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

  private CollectionResourceClient createLoansStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient loanStorageClient;

    loanStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/loan-storage/loans"));

    return loanStorageClient;
  }

  private CollectionResourceClient createItemsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient itemsStorageClient;

    itemsStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/item-storage/items"));

    return itemsStorageClient;
  }

  private CollectionResourceClient createHoldingsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient holdingsStorageClient;

    holdingsStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/holdings-storage/holdings"));

    return holdingsStorageClient;
  }

  private CollectionResourceClient createLocationsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient locationStorageClient;

    locationStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/shelf-locations"));

    return locationStorageClient;
  }

  private CollectionResourceClient createInstanceStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient instancesStorageClient;

    instancesStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/instance-storage/instances"));

    return instancesStorageClient;
  }

  private String itemStatusFrom(JsonObject loan) {
    switch(loan.getJsonObject("status").getString("name")) {
      case "Open":
        return CHECKED_OUT;

      case "Closed":
        return AVAILABLE;

      default:
        //TODO: Need to add validation to stop this situation
        return "";
    }
  }

  private JsonObject createItemSummary(
    JsonObject item,
    JsonObject instance,
    JsonObject location) {
    JsonObject itemSummary = new JsonObject();

    final String titleProperty = "title";
    final String barcodeProperty = "barcode";
    final String statusProperty = "status";

    if(instance != null && instance.containsKey(titleProperty)) {
      itemSummary.put(titleProperty, instance.getString(titleProperty));
    } else {
      itemSummary.put(titleProperty, item.getString(titleProperty));
    }

    if(item.containsKey(barcodeProperty)) {
      itemSummary.put(barcodeProperty, item.getString(barcodeProperty));
    }

    if(item.containsKey(statusProperty)) {
      itemSummary.put(statusProperty, item.getJsonObject(statusProperty));
    }

    if(location != null && location.containsKey("name")) {
      itemSummary.put("location", new JsonObject()
        .put("name", location.getString("name")));
    }

    return itemSummary;
  }

  private JsonObject extendedLoan(
    JsonObject loan,
    JsonObject item,
    JsonObject instance,
    JsonObject location) {

    loan.put("item", createItemSummary(item, instance, location));

    //No need to pass on the itemStatus property, as only used to populate the history
    //and could be confused with aggregation of current status
    loan.remove("itemStatus");

    return loan;
  }

  private static String multipleRecordsCqlQuery(List<String> recordIds) {
    if(recordIds.isEmpty()) {
      return null;
    }
    else {
      String query = String.format("id=(%s)", recordIds.stream()
        .map(String::toString)
        .distinct()
        .collect(Collectors.joining(" or ")));

      try {
        return URLEncoder.encode(query, "UTF-8");

      } catch (UnsupportedEncodingException e) {
        log.error(String.format("Cannot encode query %s", query));
        return null;
      }
    }
  }
}
