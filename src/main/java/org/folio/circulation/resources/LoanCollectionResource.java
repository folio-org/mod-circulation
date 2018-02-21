package org.folio.circulation.resources;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatusAssistant.updateItemStatus;
import static org.folio.circulation.support.CommonFailures.reportFailureToFetchInventoryRecords;
import static org.folio.circulation.support.CommonFailures.reportItemRelatedValidationError;

public class LoanCollectionResource {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String rootPath;

  public LoanCollectionResource(String rootPath) {
    this.rootPath = rootPath;
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);

    routeRegistration.create(this::create);
    routeRegistration.get(this::get);
    routeRegistration.getMany(this::getMany);
    routeRegistration.replace(this::replace);
    routeRegistration.delete(this::delete);
    routeRegistration.deleteAll(this::empty);
  }

  private void create(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context);

    JsonObject loan = routingContext.getBodyAsJson();

    defaultStatusAndAction(loan);

    String itemId = loan.getString("itemId");

    InventoryFetcher inventoryFetcher = InventoryFetcher.create(clients);

    CompletableFuture<InventoryRecords> inventoryRecordsCompleted
      = inventoryFetcher.fetch(itemId, t ->
      reportFailureToFetchInventoryRecords(routingContext, t));

    CompletableFuture<Void> allCompleted = CompletableFuture.allOf(
      inventoryRecordsCompleted);

    allCompleted.exceptionally(t -> {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("At least one request for additional information failed: %s", t));

      return null;
    });

    allCompleted.thenAccept(v -> {
      InventoryRecords inventoryRecords = inventoryRecordsCompleted.join();

      JsonObject item = inventoryRecords.getItem();
      JsonObject holding = inventoryRecords.getHolding();
      JsonObject instance = inventoryRecords.getInstance();

      if(item == null) {
        reportItemRelatedValidationError(routingContext, itemId, "Item does not exist");
      }
      else {
        updateItemStatus(itemId, itemStatusFrom(loan),
          clients.itemsStorage(), routingContext.response(), updatedItem -> {

          loan.put("itemStatus", updatedItem.getJsonObject("status").getString("name"));

          lookupLoanPolicyId(loan, updatedItem, holding, clients.usersStorage(),
            clients.loanRules(), routingContext.response(), loanPolicyIdJson -> {

            loan.put("loanPolicyId", loanPolicyIdJson.getString("loanPolicyId"));

            clients.loansStorage().post(loan, response -> {
              if(response.getStatusCode() == 201) {
                JsonObject createdLoan = response.getJson();

                final String locationId = determineLocationIdForItem(updatedItem, holding);

                clients.locationsStorage().get(locationId, locationResponse -> {
                  if(locationResponse.getStatusCode() == 200) {
                    JsonResponse.created(routingContext.response(),
                      extendedLoan(createdLoan, updatedItem, holding, instance,
                        locationResponse.getJson()));
                  }
                  else {
                    log.warn(
                      String.format("Could not get location %s for item %s",
                        locationId, itemId ));

                    JsonResponse.created(routingContext.response(),
                      extendedLoan(createdLoan, item, holding, instance, null));
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
    });
  }

  private void replace(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context);

    String id = routingContext.request().getParam("id");

    JsonObject loan = routingContext.getBodyAsJson();

    defaultStatusAndAction(loan);

    String itemId = loan.getString("itemId");

    //TODO: Either converge the schema (based upon conversations about sharing
    // schema and including referenced resources or switch to include properties
    // rather than exclude properties
    JsonObject storageLoan = loan.copy();
    storageLoan.remove("item");
    storageLoan.remove("itemStatus");

    updateItemStatus(itemId, itemStatusFrom(loan),
      clients.itemsStorage(), routingContext.response(), item -> {
        storageLoan.put("itemStatus", item.getJsonObject("status").getString("name"));
        clients.loansStorage().put(id, storageLoan, response -> {
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
    Clients clients = Clients.create(context);

    String id = routingContext.request().getParam("id");

    clients.loansStorage().get(id, loanResponse -> {
      if(loanResponse.getStatusCode() == 200) {
        JsonObject loan = new JsonObject(loanResponse.getBody());
        String itemId = loan.getString("itemId");

        InventoryFetcher fetcher = new InventoryFetcher(clients.itemsStorage(),
          clients.holdingsStorage(), clients.instancesStorage());

        CompletableFuture<InventoryRecords> inventoryRecordsCompleted =
          fetcher.fetch(itemId, t ->
            reportFailureToFetchInventoryRecords(routingContext, t));

        inventoryRecordsCompleted.thenAccept(r -> {
          JsonObject item = r.getItem();
          JsonObject holding = r.getHolding();
          JsonObject instance = r.getInstance();

          final String locationId = determineLocationIdForItem(item, holding);

          clients.locationsStorage().get(locationId,
            locationResponse -> {
              if (locationResponse.getStatusCode() == 200) {
                JsonResponse.success(routingContext.response(),
                  extendedLoan(loan, item, holding, instance,
                    locationResponse.getJson()));
              } else {
                log.warn(
                  String.format("Could not get location %s for item %s",
                    locationId, itemId));

                JsonResponse.success(routingContext.response(),
                  extendedLoan(loan, item, holding, instance,
                    null));
              }
            });
        });
      }
      else {
        ForwardResponse.forward(routingContext.response(), loanResponse);
      }
    });
  }

  private void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context);

    String id = routingContext.request().getParam("id");

    clients.loansStorage().delete(id, response -> {
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
    Clients clients = Clients.create(context);

    clients.loansStorage().getMany(routingContext.request().query(), loansResponse -> {
      if(loansResponse.getStatusCode() == 200) {
        final MultipleRecordsWrapper wrappedLoans = MultipleRecordsWrapper.fromRequestBody(
          loansResponse.getBody(), "loans");

        if(wrappedLoans.isEmpty()) {
          JsonResponse.success(routingContext.response(),
            wrappedLoans.toJson());

          return;
        }

        final Collection<JsonObject> loans = wrappedLoans.getRecords();

        List<String> itemIds = loans.stream()
          .map(loan -> loan.getString("itemId"))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

        InventoryFetcher inventoryFetcher = InventoryFetcher.create(clients);

        CompletableFuture<MultipleInventoryRecords> inventoryRecordsFetched =
          inventoryFetcher.fetch(itemIds, e ->
            ServerErrorResponse.internalError(routingContext.response(), e.toString()));

        inventoryRecordsFetched.thenAccept(records -> {
          List<String> locationIds = records.getItems().stream()
            .map(item -> determineLocationIdForItem(item,
              records.findHoldingById(item.getString("holdingsRecordId")).orElse(null)))
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());

          CompletableFuture<Response> locationsFetched = new CompletableFuture<>();

          String locationsQuery = CqlHelper.multipleRecordsCqlQuery(locationIds);

          clients.locationsStorage().getMany(locationsQuery, locationIds.size(), 0,
            locationsFetched::complete);

          locationsFetched.thenAccept(locationsResponse -> {
            if(locationsResponse.getStatusCode() != 200) {
              ServerErrorResponse.internalError(routingContext.response(),
                String.format("Locations request (%s) failed %s: %s",
                  locationsQuery, locationsResponse.getStatusCode(),
                  locationsResponse.getBody()));
              return;
            }

            loans.forEach( loan -> {
              Optional<JsonObject> possibleItem = records.findItemById(
                loan.getString("itemId"));

              //No need to pass on the itemStatus property,
              // as only used to populate the history
              //and could be confused with aggregation of current status
              loan.remove("itemStatus");

              Optional<JsonObject> possibleInstance = Optional.empty();

              if(possibleItem.isPresent()) {
                JsonObject item = possibleItem.get();

                Optional<JsonObject> possibleHolding = records.findHoldingById(
                  item.getString("holdingsRecordId"));

                if(possibleHolding.isPresent()) {
                  JsonObject holding = possibleHolding.get();

                  possibleInstance = records.findInstanceById(
                    holding.getString("instanceId"));
                }

                List<JsonObject> locations = JsonArrayHelper.toList(
                  locationsResponse.getJson().getJsonArray("shelflocations"));

                Optional<JsonObject> possibleLocation = locations.stream()
                  .filter(location -> location.getString("id").equals(
                    determineLocationIdForItem(item, possibleHolding.orElse(null))))
                  .findFirst();

                loan.put("item", createItemSummary(item,
                  possibleInstance.orElse(null),
                    possibleHolding.orElse(null),
                    possibleLocation.orElse(null)));
              }
            });

            JsonResponse.success(routingContext.response(),
              wrappedLoans.toJson());
          });
        });
      }
      else {
        ServerErrorResponse.internalError(routingContext.response(),
          "Failed to fetch loans from storage");
      }
    });
  }

  private void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context);

    clients.loansStorage().delete(response -> {
      if(response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
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

  private void defaultStatusAndAction(JsonObject loan) {
    if(!loan.containsKey("status")) {
      loan.put("status", new JsonObject().put("name", "Open"));

      if(!loan.containsKey("action")) {
        loan.put("action", "checkedout");
      }
    }
  }

  private JsonObject createItemSummary(
    JsonObject item,
    JsonObject instance,
    JsonObject holding,
    JsonObject location) {
    JsonObject itemSummary = new JsonObject();

    final String titleProperty = "title";
    final String barcodeProperty = "barcode";
    final String statusProperty = "status";
    final String holdingsRecordIdProperty = "holdingsRecordId";
    final String instanceIdProperty = "instanceId";

    if(instance != null && instance.containsKey(titleProperty)) {
      itemSummary.put(titleProperty, instance.getString(titleProperty));
    } else if (item.containsKey("title")) {
      itemSummary.put(titleProperty, item.getString(titleProperty));
    }

    if(item.containsKey(barcodeProperty)) {
      itemSummary.put(barcodeProperty, item.getString(barcodeProperty));
    }

    if(item.containsKey(holdingsRecordIdProperty)) {
      itemSummary.put(holdingsRecordIdProperty, item.getString(holdingsRecordIdProperty));
    }

    if(holding != null && holding.containsKey(instanceIdProperty)) {
      itemSummary.put(instanceIdProperty, holding.getString(instanceIdProperty));
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
    JsonObject holding,
    JsonObject instance,
    JsonObject location) {

    if(item != null) {
      loan.put("item", createItemSummary(item, instance, holding, location));
    }

    //No need to pass on the itemStatus property, as only used to populate the history
    //and could be confused with aggregation of current status
    loan.remove("itemStatus");

    return loan;
  }

  private void lookupLoanPolicyId(
    JsonObject loan,
    JsonObject item,
    JsonObject holding,
    CollectionResourceClient usersStorageClient,
    LoanRulesClient loanRulesClient,
    HttpServerResponse responseToClient,
    Consumer<JsonObject> onSuccess) {

    if(item == null) {
      ServerErrorResponse.internalError(responseToClient,
        "Unable to process claim for unknown item");
      return;
    }

    if(holding == null) {
      ServerErrorResponse.internalError(responseToClient,
        "Unable to process claim for unknown holding");
      return;
    }

    String userId = loan.getString("userId");

    String loanTypeId = determineLoanTypeForItem(item);
    String locationId = determineLocationIdForItem(item, holding);

    //Got instance record, we're good to continue
    String materialTypeId = item.getString("materialTypeId");

    usersStorageClient.get(userId, getUserResponse -> {
      if(getUserResponse.getStatusCode() == 404) {
        ServerErrorResponse.internalError(responseToClient, "Unable to locate User");
      }
      else if(getUserResponse.getStatusCode() != 200) {
        ForwardResponse.forward(responseToClient, getUserResponse);
      } else {
        //Got user record, we're good to continue
        JsonObject user = getUserResponse.getJson();

        String patronGroup = user.getString("patronGroup");

        loanRulesClient.applyRules(loanTypeId, locationId, materialTypeId,
          patronGroup, response -> response.bodyHandler(body -> {

          Response getPolicyResponse = Response.from(response, body);

          if (getPolicyResponse.getStatusCode() == 404) {
            ServerErrorResponse.internalError(responseToClient, "Unable to locate loan policy");
          } else if (getPolicyResponse.getStatusCode() != 200) {
            ForwardResponse.forward(responseToClient, getPolicyResponse);
          } else {
            JsonObject policyIdJson = getPolicyResponse.getJson();
            onSuccess.accept(policyIdJson);
          }
        }));
      }
    });
  }

  private String determineLoanTypeForItem(JsonObject item) {
    return item.containsKey("temporaryLoanTypeId") && !item.getString("temporaryLoanTypeId").isEmpty()
      ? item.getString("temporaryLoanTypeId")
      : item.getString("permanentLoanTypeId");
  }

  private String determineLocationIdForItem(JsonObject item, JsonObject holding) {
    if(item != null && item.containsKey("temporaryLocationId")) {
      return item.getString("temporaryLocationId");
    }
    else if(holding != null && holding.containsKey("permanentLocationId")) {
      return holding.getString("permanentLocationId");
    }
    else if(item != null && item.containsKey("permanentLocationId")) {
      return item.getString("permanentLocationId");
    }
    else {
      return null;
    }
  }
}
