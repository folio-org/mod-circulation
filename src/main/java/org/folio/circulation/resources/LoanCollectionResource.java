package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueFetcher;
import org.folio.circulation.domain.UpdateRequestQueue;
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
import java.util.function.Function;
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

    final String itemId = loan.getString("itemId");
    final String requestingUserId = loan.getString("userId");

    final InventoryFetcher inventoryFetcher = InventoryFetcher.create(clients);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);

//    TODO: Reinstate this logic in http result handling
//    allCompleted.exceptionally(t -> {
//      ServerErrorResponse.internalError(routingContext.response(),
//        String.format("At least one request for additional information failed: %s", t));

    inventoryFetcher.fetch(itemId)
      .thenApply(r -> checkItemExists(r, itemId))
      .thenCombineAsync(requestQueueFetcher.get(itemId), this::addRequestQueue)
      .thenApply(r -> r.map(records -> records.changeLoan(loan)))
      .thenCombineAsync(getUser(requestingUserId, clients.usersStorage()), this::addUser)
      .thenComposeAsync(r -> r.after(records -> getLocation(records, clients)))
      .thenComposeAsync(r -> r.after(records -> updateItemStatus(records,
        itemStatusFrom(loan), clients.itemsStorage())))
      .thenComposeAsync(r -> r.after(records -> lookupLoanPolicyId(
        records, clients.loanRules())))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(records -> createLoan(records, clients)))
      .thenApply(r -> r.map(this::extendedLoan))
      .thenApply(CreatedHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private CompletableFuture<HttpResult<JsonObject>> getUser(
    String userId,
    CollectionResourceClient usersClient) {

    CompletableFuture<Response> getUserCompleted = new CompletableFuture<>();

    usersClient.get(userId, getUserCompleted::complete);

    final Function<Response, HttpResult<JsonObject>> mapResponse = response -> {
      if(response.getStatusCode() == 404) {
        return HttpResult.failure(new ServerErrorFailure("Unable to locate User"));
      }
      else if(response.getStatusCode() != 200) {
        return HttpResult.failure(new ForwardOnFailure(response));
      }
      else {
        //Got user record, we're good to continue
        return HttpResult.success(response.getJson());
      }
    };

    return getUserCompleted
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> getLocation(
    LoanAndRelatedRecords relatedRecords,
    Clients clients) {

    final String locationId = determineLocationIdForItem(
      relatedRecords.inventoryRecords.item, relatedRecords.inventoryRecords.holding);

    return getLocation(locationId,
      relatedRecords.inventoryRecords.item.getString("id"), clients)
      .thenApply(result -> result.map(relatedRecords::changeLocation));
  }

  private CompletableFuture<HttpResult<JsonObject>> getLocation(
    String locationId,
    String itemId,
    Clients clients) {

    CompletableFuture<Response> getUserCompleted = new CompletableFuture<>();

    clients.locationsStorage().get(locationId, getUserCompleted::complete);

    //TODO: Add functions to explicitly distinguish between fatal not found
    // and allowable not found
    final Function<Response, HttpResult<JsonObject>> mapResponse = response -> {
      if(response != null && response.getStatusCode() == 200) {
        return HttpResult.success(response.getJson());
      }
      else {
        log.warn(
          String.format("Could not get location %s for item %s",
            locationId, itemId));

        return HttpResult.success(null);
      }
    };

    return getUserCompleted
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  private HttpResult<InventoryRecords> checkItemExists(
    HttpResult<InventoryRecords> result, String itemId) {

    return result.next((InventoryRecords inventoryRecords) -> {
      if(inventoryRecords.getItem() == null) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Item does not exist", "itemId", itemId));
      }
      else {
        return result;
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

    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final InventoryFetcher inventoryFetcher = InventoryFetcher.create(clients);

    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);

    inventoryFetcher.fetch(itemId)
      .thenApplyAsync(r -> checkItemExists(r, itemId))
      .thenCombineAsync(requestQueueFetcher.get(itemId), this::addRequestQueue)
      .thenComposeAsync(relatedRecordsResult -> relatedRecordsResult.after(relatedRecords1 ->
        updateItemStatus(relatedRecordsResult.value(), itemStatusFrom(loan), clients.itemsStorage())))
      .thenComposeAsync(updateItemResult -> updateItemResult.after(relatedRecords -> updateLoan(clients, id, loan, relatedRecords)))
      .thenComposeAsync(updateLoanResult -> updateLoanResult.after(requestQueueUpdate::onCheckIn))
      .thenApply(NoContentHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
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

        CompletableFuture<HttpResult<InventoryRecords>> inventoryRecordsCompleted =
          fetcher.fetch(itemId);

        inventoryRecordsCompleted.thenAccept(result -> {
          if(result.failed()) {
            result.cause().writeTo(routingContext.response());
            return;
          }

          JsonObject item = result.value().getItem();
          JsonObject holding = result.value().getHolding();
          JsonObject instance = result.value().getInstance();

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

  private JsonObject extendedLoan(LoanAndRelatedRecords relatedRecords) {
    return extendedLoan(relatedRecords.loan,
      relatedRecords.inventoryRecords.item,
      relatedRecords.inventoryRecords.holding,
      relatedRecords.inventoryRecords.instance,
      relatedRecords.location);
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

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> lookupLoanPolicyId(
    LoanAndRelatedRecords relatedRecords,
    LoanRulesClient loanRulesClient) {

    return lookupLoanPolicyId(relatedRecords.inventoryRecords.getItem(),
      relatedRecords.inventoryRecords.holding, relatedRecords.requestingUser, loanRulesClient)
      .thenApply(result -> result.map(relatedRecords::changeLoanPolicy));
  }

  private CompletableFuture<HttpResult<String>> lookupLoanPolicyId(
    JsonObject item,
    JsonObject holding,
    JsonObject user,
    LoanRulesClient loanRulesClient) {

    CompletableFuture<HttpResult<String>> findLoanPolicyCompleted
      = new CompletableFuture<>();

    lookupLoanPolicyId(item, holding, user, loanRulesClient,
      findLoanPolicyCompleted::complete);

    return findLoanPolicyCompleted;
  }

  private void lookupLoanPolicyId(
    JsonObject item,
    JsonObject holding,
    JsonObject user,
    LoanRulesClient loanRulesClient,
    Consumer<HttpResult<String>> onFinished) {

    if(item == null) {
      onFinished.accept(HttpResult.failure(
        new ServerErrorFailure("Unable to process claim for unknown item")));
      return;
    }

    if(holding == null) {
      onFinished.accept(HttpResult.failure(
        new ServerErrorFailure("Unable to process claim for unknown holding")));
      return;
    }

    String loanTypeId = determineLoanTypeForItem(item);
    String locationId = determineLocationIdForItem(item, holding);

    //Got instance record, we're good to continue
    String materialTypeId = item.getString("materialTypeId");

    String patronGroup = user.getString("patronGroup");
    loanRulesClient.applyRules(loanTypeId, locationId, materialTypeId,
      patronGroup, response -> response.bodyHandler(body -> {
        Response getPolicyResponse = Response.from(response, body);

        if (getPolicyResponse.getStatusCode() == 404) {
          onFinished.accept(HttpResult.failure(
            new ServerErrorFailure("Unable to locate loan policy")));
        } else if (getPolicyResponse.getStatusCode() != 200) {
          onFinished.accept(HttpResult.failure(
            new ForwardOnFailure(getPolicyResponse)));
        } else {
          String policyId = getPolicyResponse.getJson().getString("loanPolicyId");
          onFinished.accept(HttpResult.success(policyId));
        }
      }));
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

  private String getItemStatus(JsonObject item) {
    return item.getJsonObject("status").getString("name");
  }

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> updateLoan(
    Clients clients,
    String id,
    JsonObject loan,
    LoanAndRelatedRecords relatedRecords) {

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> onUpdated = new CompletableFuture<>();

    JsonObject storageLoan = convertLoanToStorageRepresentation(loan,
      relatedRecords.inventoryRecords.getItem());

    clients.loansStorage().put(id, storageLoan, response -> {
      if (response.getStatusCode() == 204) {
        onUpdated.complete(HttpResult.success(relatedRecords));
      } else {
        onUpdated.complete(HttpResult.failure(new ServerErrorFailure("Failed to update loan")));
      }
    });

    return onUpdated;
  }

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> createLoan(
    LoanAndRelatedRecords relatedRecords,
    Clients clients) {

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCreated = new CompletableFuture<>();

    JsonObject loan = relatedRecords.loan;

    loan.put("loanPolicyId", relatedRecords.loanPolicyId);
    loan.put("itemStatus", getItemStatus(relatedRecords.inventoryRecords.item));

    clients.loansStorage().post(loan, response -> {
      if (response.getStatusCode() == 201) {
        onCreated.complete(HttpResult.success(
          relatedRecords.changeLoan(response.getJson())));
      } else {
        onCreated.complete(HttpResult.failure(new ForwardOnFailure(response)));
      }
    });

    return onCreated;
  }

  private JsonObject convertLoanToStorageRepresentation(
    JsonObject loan,
    JsonObject item) {

    JsonObject storageLoan = loan.copy();

    storageLoan.remove("item");
    storageLoan.remove("itemStatus");
    storageLoan.put("itemStatus", getItemStatus(item));

    return storageLoan;
  }

  private HttpResult<LoanAndRelatedRecords> addRequestQueue(
    HttpResult<InventoryRecords> inventoryRecordsResult,
    HttpResult<RequestQueue> requestQueueResult) {

    if(inventoryRecordsResult.failed()) {
      return HttpResult.failure(inventoryRecordsResult.cause());
    }
    else if(requestQueueResult.failed()) {
      return HttpResult.failure(requestQueueResult.cause());
    }
    else {
      return HttpResult.success(new LoanAndRelatedRecords(
        inventoryRecordsResult.value(), requestQueueResult.value(), null, null,
        null, null));
    }
  }

  private HttpResult<LoanAndRelatedRecords> addUser(
    HttpResult<LoanAndRelatedRecords> relatedRecordsResult,
    HttpResult<JsonObject> getUserResult) {

    if(relatedRecordsResult.failed()) {
      return HttpResult.failure(relatedRecordsResult.cause());
    }
    else if(getUserResult.failed()) {
      return HttpResult.failure(getUserResult.cause());
    }
    else {
      return HttpResult.success(relatedRecordsResult.value()
        .changeUser(getUserResult.value()));
    }
  }
}
