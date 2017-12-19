package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.io.UnsupportedEncodingException;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
    CollectionResourceClient usersStorageClient;
    CollectionResourceClient instancesStorageClient;
    OkapiHttpClient client;

    try {
      client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      locationsStorageClient = createLocationsStorageClient(client, context);
      usersStorageClient = createUsersStorageClient(client, context);
      instancesStorageClient = createInstancesStorageClient(client, context);
              
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
        //acquire loan policy here before creating loan
        lookupLoanPolicyId(loan, item, instancesStorageClient, usersStorageClient,
                client, routingContext.response(), context, loanPolicyId -> {
          loan.put("loanPolicyId", loanPolicyId.getString("loanPolicyId"));
          loansStorageClient.post(loan, response -> {
            if(response.getStatusCode() == 201) {
              JsonObject createdLoan = response.getJson();

              String holdingId = item.getString("holdingsRecordId");

              holdingsStorageClient.get(holdingId, holdingResponse -> {
                if(item.containsKey("temporaryLocationId")) {
                  String locationId = item.getString("temporaryLocationId");
                  locationsStorageClient.get(locationId,
                    locationResponse -> {
                      if (locationResponse.getStatusCode() == 200) {
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item, locationResponse.getJson()));
                      } else {
                        log.warn(
                          String.format("Could not get location %s for item %s",
                            locationId, itemId));
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item, null));
                      }
                    });
                } else if(holdingResponse.getStatusCode() == 200
                  && holdingResponse.getJson().containsKey("permanentLocationId")) {

                  String locationId = holdingResponse.getJson().getString("permanentLocationId");

                  locationsStorageClient.get(locationId,
                    locationResponse -> {
                      if(locationResponse.getStatusCode() == 200) {
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item, locationResponse.getJson()));
                      }
                      else {
                        log.warn(
                          String.format(
                            "Could not get location %s for item %s from holding %s",
                            locationId, itemId, holdingId));
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item, null));
                      }
                    });
                } else if(item.containsKey("permanentLocationId")) {
                  String locationId = item.getString("permanentLocationId");
                  locationsStorageClient.get(locationId,
                    locationResponse -> {
                      if(locationResponse.getStatusCode() == 200) {
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item, locationResponse.getJson()));
                      }
                      else {
                        log.warn(
                          String.format("Could not get location %s for item %s",
                            locationId, itemId ));
                        JsonResponse.created(routingContext.response(),
                          extendedLoan(createdLoan, item, null));
                      }
                  });
                }
                else {
                  JsonResponse.created(routingContext.response(),
                    extendedLoan(createdLoan, item, null));
                }
              });
          }
          else {
            ForwardResponse.forward(routingContext.response(), response);
          }
        });
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

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      locationsStorageClient = createLocationsStorageClient(client, context);
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
              if(item.containsKey("temporaryLocationId")) {
                String locationId = item.getString("temporaryLocationId");
                locationsStorageClient.get(locationId,
                  locationResponse -> {
                    if(locationResponse.getStatusCode() == 200) {
                      JsonResponse.success(routingContext.response(),
                        extendedLoan(loan, item, locationResponse.getJson()));
                    }
                    else {
                      log.warn(
                        String.format("Could not get location %s for item %s",
                          locationId, itemId ));

                      JsonResponse.success(routingContext.response(),
                        extendedLoan(loan, item, null));
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
                        extendedLoan(loan, item, locationResponse.getJson()));
                    }
                    else {
                      log.warn(
                        String.format("Could not get location %s for item %s",
                          locationId, itemId ));

                      JsonResponse.success(routingContext.response(),
                        extendedLoan(loan, item, null));
                    }
                  });
              }
              else if (item.containsKey("permanentLocationId")) {
                String locationId = item.getString("permanentLocationId");
                locationsStorageClient.get(locationId,
                  locationResponse -> {
                    if(locationResponse.getStatusCode() == 200) {
                      JsonResponse.success(routingContext.response(),
                        extendedLoan(loan, item, locationResponse.getJson()));
                    }
                    else {
                      log.warn(
                        String.format("Could not get location %s for item %s",
                          locationId, itemId ));

                      JsonResponse.success(routingContext.response(),
                        extendedLoan(loan, item, null));
                    }
                  });
              }
              else {
                JsonResponse.success(routingContext.response(),
                  extendedLoan(loan, item, null));
              }
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

        List<CompletableFuture<Response>> allItemFutures = new ArrayList<>();
        List<CompletableFuture<Response>> allLocationFutures = new ArrayList<>();

        loans.forEach(loanResource -> {
          CompletableFuture<Response> newFuture = new CompletableFuture<>();

          allItemFutures.add(newFuture);

          itemsStorageClient.get(loanResource.getString("itemId"),
            newFuture::complete);
        });

        CompletableFuture<Void> allItemsFetchedFuture =
          CompletableFuture.allOf(allItemFutures.toArray(new CompletableFuture<?>[] { }));

        allItemsFetchedFuture.thenAccept(v -> {
          List<Response> itemResponses = allItemFutures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());

          itemResponses.stream()
            .filter(itemResponse -> itemResponse.getStatusCode() == 200)
            .forEach(itemResponse -> {

              JsonObject item = itemResponse.getJson();

              if(item.containsKey("temporaryLocationId")) {
                CompletableFuture<Response> newFuture = new CompletableFuture<>();

                allLocationFutures.add(newFuture);

                locationsClient.get(item.getString("temporaryLocationId"),
                  newFuture::complete);
              }
              else if(item.containsKey("permanentLocationId")) {
                CompletableFuture<Response> newFuture = new CompletableFuture<>();

                allLocationFutures.add(newFuture);

                locationsClient.get(item.getString("permanentLocationId"),
                  newFuture::complete);
              }
          });

          CompletableFuture<Void> allLocationsFetchedFuture =
            CompletableFuture.allOf(allLocationFutures.toArray(new CompletableFuture<?>[] { }));

          allLocationsFetchedFuture.thenAccept(w -> {
            List<Response> locationResponses = allLocationFutures.stream().
              map(CompletableFuture::join).
              collect(Collectors.toList());

            loans.forEach( loan -> {
              Optional<JsonObject> possibleItem = itemResponses.stream()
                .filter(itemResponse -> itemResponse.getStatusCode() == 200)
                .map(Response::getJson)
                .filter(item -> item.getString("id").equals(loan.getString("itemId")))
                .findFirst();

              //No need to pass on the itemStatus property, as only used to populate the history
              //and could be confused with aggregation of current status
              loan.remove("itemStatus");

              if(possibleItem.isPresent()) {
                JsonObject item = possibleItem.get();

                Optional<JsonObject> possiblePermanentLocation = locationResponses.stream()
                  .filter(locationResponse -> locationResponse.getStatusCode() == 200)
                  .map(Response::getJson)
                  .filter(location -> location.getString("id").equals(item.getString("permanentLocationId")))
                  .findFirst();

                Optional<JsonObject> possibleTemporaryLocation = locationResponses.stream()
                  .filter(locationResponse -> locationResponse.getStatusCode() == 200)
                  .map(Response::getJson)
                  .filter(location -> location.getString("id").equals(item.getString("temporaryLocationId")))
                  .findFirst();

                loan.put("item", createItemSummary(item,
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
      client, context.getOkapiBasedUrl("/loan-storage/loans"),
      context.getTenantId());

    return loanStorageClient;
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

  private CollectionResourceClient createHoldingsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient holdingsStorageClient;

    holdingsStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/holdings-storage/holdings"),
      context.getTenantId());

    return holdingsStorageClient;
  }

  private CollectionResourceClient createLocationsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient loanStorageClient;

    loanStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/shelf-locations"),
      context.getTenantId());

    return loanStorageClient;
  }
  
  private CollectionResourceClient createUsersStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {
    
    CollectionResourceClient usersStorageClient;
    
    usersStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/users"), context.getTenantId());
    
    return usersStorageClient;
  }
  
  private CollectionResourceClient createInstancesStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {
    CollectionResourceClient instancesStorageClient;
    instancesStorageClient = new CollectionResourceClient(client,
      context.getOkapiBasedUrl("/instance-storage/instances"), context.getTenantId());
    
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

  private JsonObject createItemSummary(JsonObject item, JsonObject location) {
    JsonObject itemSummary = new JsonObject();

    itemSummary.put("title", item.getString("title"));

    if(item.containsKey("barcode")) {
      itemSummary.put("barcode", item.getString("barcode"));
    }

    if(item.containsKey("status")) {
      itemSummary.put("status", item.getJsonObject("status"));
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
    JsonObject location) {

    loan.put("item", createItemSummary(item, location));

    //No need to pass on the itemStatus property, as only used to populate the history
    //and could be confused with aggregation of current status
    loan.remove("itemStatus");

    return loan;
  }
  
  private void lookupLoanPolicyId(
    JsonObject loan,
    JsonObject item,
    CollectionResourceClient instancesStorageClient,
    CollectionResourceClient usersStorageClient,
    OkapiHttpClient client,
    HttpServerResponse responseToClient,
    WebContext context,
    Consumer<JsonObject> onSuccess ) {
      String instanceId = item.getString("instanceId");
      String userId = loan.getString("userId");
      String[] loanTypeId =  { null };
      if(item.containsKey("temporaryLoanTypeId") && !item.getString("temporaryLoanTypeId").isEmpty()) {
        loanTypeId[0] = item.getString("temporaryLoanTypeId");
      } else {
        loanTypeId[0] = item.getString("permanentLoanTypeId");
      }
      String[] locationId = { null };
      if(item.containsKey("temporaryLocationId") && !item.getString("temporaryLocationId").isEmpty()) {
        locationId[0] = item.getString("temporaryLocationId");
      } else {
        locationId[0] = item.getString("permanentLocationId");
      }
      instancesStorageClient.get(instanceId, getInstanceResponse -> {
        if(getInstanceResponse.getStatusCode() != 200) {
          if(getInstanceResponse.getStatusCode() == 404) {
            ServerErrorResponse.internalError(responseToClient, "Unable to locate Instance");
          } else {
            ForwardResponse.forward(responseToClient, getInstanceResponse);
          }          
        } else {
          //Got instance record, we're good to continue
          JsonObject instance = getInstanceResponse.getJson();
          String[] instanceTypeId = { instance.getString("instanceTypeId") };
          usersStorageClient.get(userId, getUserResponse -> {
            if(getUserResponse.getStatusCode() != 200) {
              if(getUserResponse.getStatusCode() == 404) {
                ServerErrorResponse.internalError(responseToClient, "Unable to locate User");
              } else {
                 ForwardResponse.forward(responseToClient, getUserResponse);
              }
            } else {
              //Got user record, we're good to continue
              JsonObject user = getUserResponse.getJson();
              try {
                client.get(context.getOkapiBasedUrl("/circulation/loan-rules/apply") + 
                  String.format(
                    "item_type_id=%s&loan_type_id=%s&patron_type_id=%s&shelving_location_id=%s",
                    instanceTypeId[0], loanTypeId[0], user.getString("patronGroup"), locationId[0]),
                    response -> {
                      response.bodyHandler( body -> {
                        Response getPolicyResponse = Response.from(response, body);
                        if(getPolicyResponse.getStatusCode() != 200) {
                          if(getPolicyResponse.getStatusCode() != 404) {
                            ServerErrorResponse.internalError(responseToClient, "Unable to locate loan policy");
                          } else {
                             ForwardResponse.forward(responseToClient, getPolicyResponse);
                          }
                        } else {
                          JsonObject policyIdJson = getPolicyResponse.getJson();
                           onSuccess.accept(policyIdJson);
                        }
                      });
                });
              } catch(MalformedURLException m) {
                ServerErrorResponse.internalError(responseToClient, "Error forming URL to loan-rules endpoint");
              }
            }
          });
        }
      });
  
  }
  
  static String urlEncodeUTF8(String s) {
    try {
      return URLEncoder.encode(s, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new UnsupportedOperationException(e);
    }
  }
}
