package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.*;
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

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;

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

  //TODO: Add exceptional completion of futures to create failed results
  private void create(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context);

    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final UserFetcher userFetcher = new UserFetcher(clients);

    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);

    JsonObject loan = routingContext.getBodyAsJson();

    defaultStatusAndAction(loan);

    final String itemId = loan.getString("itemId");
    final String requestingUserId = loan.getString("userId");

    completedFuture(HttpResult.success(new LoanAndRelatedRecords(loan)))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenCombineAsync(inventoryFetcher.fetch(loan), this::addInventoryRecords)
      .thenApply(this::refuseWhenItemDoesNotExist)
      .thenApply(this::refuseWhenHoldingDoesNotExist)
      .thenApply(this::refuseWhenItemIsAlreadyCheckedOut)
      .thenCombineAsync(requestQueueFetcher.get(itemId), this::addRequestQueue)
      .thenCombineAsync(userFetcher.getUser(requestingUserId), this::addUser)
      .thenApply(this::refuseWhenUserIsNotAwaitingPickup)
      .thenComposeAsync(r -> r.after(records -> getLocation(records, clients)))
      .thenComposeAsync(r -> r.after(records -> lookupLoanPolicyId(
        records, clients.loanRules())))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onCheckOut))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenApply(r -> r.map(this::extendedLoan))
      .thenApply(CreatedJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private void replace(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);

    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);

    JsonObject loan = routingContext.getBodyAsJson();

    loan.put("id", routingContext.request().getParam("id"));

    defaultStatusAndAction(loan);

    String itemId = loan.getString("itemId");

    completedFuture(HttpResult.success(new LoanAndRelatedRecords(loan)))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenCombineAsync(inventoryFetcher.fetch(loan), this::addInventoryRecords)
      .thenApply(this::refuseWhenItemDoesNotExist)
      .thenCombineAsync(requestQueueFetcher.get(itemId), this::addRequestQueue)
      .thenComposeAsync(result -> result.after(requestQueueUpdate::onCheckIn))
      .thenComposeAsync(result -> result.after(updateItem::onLoanUpdate))
      .thenComposeAsync(result -> result.after(loanRepository::updateLoan))
      .thenApply(NoContentHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private void get(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context);
    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);

    String id = routingContext.request().getParam("id");

    getLoanFromStorage(id, clients)
      .thenComposeAsync(result ->
        result.after(loan -> getInventoryRecords(loan, inventoryFetcher)))
      .thenComposeAsync(r -> r.after(records -> getLocation(records, clients)))
      .thenApply(r -> r.map(this::extendedLoan))
      .thenApply(OkJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
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

        InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);

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
      .thenApply(result -> result.map(relatedRecords::withLoanPolicy));
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

  private HttpResult<LoanAndRelatedRecords> addInventoryRecords(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<InventoryRecords> inventoryRecordsResult) {

    return HttpResult.combine(loanResult, inventoryRecordsResult,
      LoanAndRelatedRecords::withInventoryRecords);
  }

  private HttpResult<LoanAndRelatedRecords> addRequestQueue(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<RequestQueue> requestQueueResult) {

    return HttpResult.combine(loanResult, requestQueueResult,
      LoanAndRelatedRecords::withRequestQueue);
  }

  private HttpResult<LoanAndRelatedRecords> addUser(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<JsonObject> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withRequestingUser);
  }

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> getInventoryRecords(
    LoanAndRelatedRecords loanAndRelatedRecords, InventoryFetcher inventoryFetcher) {

    return inventoryFetcher
      .fetch(loanAndRelatedRecords.loan)
      .thenApply(result -> result.map(loanAndRelatedRecords::withInventoryRecords));
  }

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> getLocation(
    LoanAndRelatedRecords relatedRecords,
    Clients clients) {

    //Cannot find location for unknown item
    if(relatedRecords.inventoryRecords.item == null) {
      return CompletableFuture.completedFuture(HttpResult.success(relatedRecords));
    }

    final String locationId = determineLocationIdForItem(
      relatedRecords.inventoryRecords.item, relatedRecords.inventoryRecords.holding);

    return getLocation(locationId,
      relatedRecords.inventoryRecords.item.getString("id"), clients)
      .thenApply(result -> result.map(relatedRecords::withLocation));
  }

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> getLoanFromStorage(
    String loanId,
    Clients clients) {

    CompletableFuture<Response> getLoanCompleted = new CompletableFuture<>();

    clients.loansStorage().get(loanId, getLoanCompleted::complete);

    final Function<Response, HttpResult<LoanAndRelatedRecords>> mapResponse = response -> {
      if(response != null && response.getStatusCode() == 200) {
        return HttpResult.success(new LoanAndRelatedRecords(response.getJson()));
      }
      else {
        return HttpResult.failure(new ForwardOnFailure(response));
      }
    };

    return getLoanCompleted
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  private CompletableFuture<HttpResult<JsonObject>> getLocation(
    String locationId,
    String itemId,
    Clients clients) {

    CompletableFuture<Response> getLocationCompleted = new CompletableFuture<>();

    clients.locationsStorage().get(locationId, getLocationCompleted::complete);

    //TODO: Add functions to explicitly distinguish between fatal not found
    // and allowable not found
    final Function<Response, HttpResult<JsonObject>> mapResponse = response -> {
      if(response != null && response.getStatusCode() == 200) {
        return HttpResult.success(response.getJson());
      }
      else {
        log.warn("Could not get location {} for item {}",
          locationId, itemId);

        return HttpResult.success(null);
      }
    };

    return getLocationCompleted
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  private HttpResult<LoanAndRelatedRecords> refuseWhenItemDoesNotExist(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      if(loan.inventoryRecords.getItem() == null) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Item does not exist", "itemId", loan.loan.getString("itemId")));
      }
      else {
        return result;
      }
    });
  }

  private HttpResult<LoanAndRelatedRecords> refuseWhenHoldingDoesNotExist(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      if(loan.inventoryRecords.getHolding() == null) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Holding does not exist", "itemId", loan.loan.getString("itemId")));
      }
      else {
        return result;
      }
    });
  }

  private HttpResult<LoanAndRelatedRecords> refuseWhenNotOpenOrClosed(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loanAndRelatedRecords -> {
      JsonObject loan = loanAndRelatedRecords.loan;

      if(loan == null) {
        return HttpResult.failure(new ServerErrorFailure(
          "Cannot check loan status when no loan"));
      }

      if(!loan.containsKey("status")) {
        return HttpResult.failure(new ServerErrorFailure(
          "Loan does not have a status"));
      }

      String status = loan.getJsonObject("status").getString("name");

      switch(status) {
        case "Open":
        case "Closed":
          return result;

        default:
          return HttpResult.failure(new ValidationErrorFailure(
            "Loan status must be \"Open\" or \"Closed\"", "status", status));
      }
    });
  }

  private HttpResult<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      final JsonObject item = loan.inventoryRecords.item;

      if(ItemStatus.isCheckedOut(item)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Item is already checked out", "itemId", item.getString("id")));
      }
      else {
        return result;
      }
    });
  }

  private HttpResult<LoanAndRelatedRecords> refuseWhenUserIsNotAwaitingPickup(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      final RequestQueue requestQueue = loan.requestQueue;
      final JsonObject requestingUser = loan.requestingUser;

      if(hasAwaitingPickupRequestForOtherPatron(requestQueue, requestingUser)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "User checking out must be requester awaiting pickup",
          "userId", loan.loan.getString("userId")));
      }
      else {
        return result;
      }
    });
  }

  private boolean hasAwaitingPickupRequestForOtherPatron(
    RequestQueue requestQueue,
    JsonObject requestingUser) {

    if(!requestQueue.hasOutstandingFulfillableRequests()) {
      return false;
    }
    else {
      final JsonObject highestPriority = requestQueue.getHighestPriorityFulfillableRequest();

      return isAwaitingPickup(highestPriority)
        && !isFor(highestPriority, requestingUser);
    }
  }

  private boolean isFor(JsonObject request, JsonObject user) {
    return StringUtils.equals(request.getString("requesterId"), user.getString("id"));
  }

  private boolean isAwaitingPickup(JsonObject highestPriority) {
    return StringUtils.equals(highestPriority.getString("status"), OPEN_AWAITING_PICKUP);
  }
}
