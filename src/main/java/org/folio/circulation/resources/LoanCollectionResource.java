package org.folio.circulation.resources;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
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
import static org.folio.circulation.support.CommonFailures.reportInvalidOkapiUrlHeader;

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
    LoanRulesClient loanRulesClient;

    try {
      client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      instancesStorageClient = createInstanceStorageClient(client, context);
      locationsStorageClient = createLocationsStorageClient(client, context);
      usersStorageClient = createUsersStorageClient(client, context);
      loanRulesClient = new LoanRulesClient(client, context);
    }
    catch (MalformedURLException e) {
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

      return;
    }

    JsonObject loan = routingContext.getBodyAsJson();

    defaultStatusAndAction(loan);

    String itemId = loan.getString("itemId");

    updateItemStatus(itemId, itemStatusFrom(loan),
      itemsStorageClient, routingContext.response(), item -> {

        loan.put("itemStatus", item.getJsonObject("status").getString("name"));

        String holdingId = item.getString("holdingsRecordId");

        holdingsStorageClient.get(holdingId, holdingResponse -> {
          final String instanceId = holdingResponse.getStatusCode() == 200
            ? holdingResponse.getJson().getString("instanceId")
            : null;

          instancesStorageClient.get(instanceId, instanceResponse -> {
            final JsonObject instance = instanceResponse.getStatusCode() == 200
              ? instanceResponse.getJson()
              : null;

            final JsonObject holding = holdingResponse.getStatusCode() == 200
              ? holdingResponse.getJson()
              : null;

            lookupLoanPolicyId(loan, item, holding, usersStorageClient,
              loanRulesClient, routingContext.response(), loanPolicyIdJson -> {

              loan.put("loanPolicyId", loanPolicyIdJson.getString("loanPolicyId"));

              loansStorageClient.post(loan, response -> {
                if(response.getStatusCode() == 201) {
                  JsonObject createdLoan = response.getJson();

                  final String locationId = determineLocationIdForItem(item, holding);

                  locationsStorageClient.get(locationId, locationResponse -> {
                    if(locationResponse.getStatusCode() == 200) {
                      JsonResponse.created(routingContext.response(),
                        extendedLoan(createdLoan, item, holding, instance,
                          locationResponse.getJson(), null));
                    }
                    else {
                      log.warn(
                        String.format("Could not get location %s for item %s",
                          locationId, itemId ));

                      JsonResponse.created(routingContext.response(),
                        extendedLoan(createdLoan, item, holding, instance, null, null));
                    }
                  });
                }
                else {
                  ForwardResponse.forward(routingContext.response(), response);
                }
              });
          });
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
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

      return;
    }

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
    CollectionResourceClient materialTypesStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      locationsStorageClient = createLocationsStorageClient(client, context);
      instancesStorageClient = createInstanceStorageClient(client, context);
      materialTypesStorageClient = createMaterialTypesStorageClient(client, context);

    }
    catch (MalformedURLException e) {
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

      return;
    }

    String id = routingContext.request().getParam("id");

    loansStorageClient.get(id, loanResponse -> {
      if(loanResponse.getStatusCode() == 200) {
        JsonObject loan = new JsonObject(loanResponse.getBody());
        String itemId = loan.getString("itemId");

        InventoryFetcher fetcher = new InventoryFetcher(itemsStorageClient,
          holdingsStorageClient, instancesStorageClient);

        CompletableFuture<InventoryRecords> inventoryRecordsCompleted =
          fetcher.fetch(itemId, t ->
            reportFailureToFetchInventoryRecords(routingContext, t));

        inventoryRecordsCompleted.thenAccept(r -> {
          JsonObject item = r.getItem();
          JsonObject holding = r.getHolding();
          JsonObject instance = r.getInstance();

          final String locationId = determineLocationIdForItem(item, holding);
          
          locationsStorageClient.get(locationId,
            locationResponse -> {
            JsonObject locationObject;
            if (locationResponse.getStatusCode() != 200) {
              log.warn(
                String.format("Could not get location %s for item %s",
                  locationId, itemId));
              locationObject = null;
            } else {
              locationObject = locationResponse.getJson();
            }
            String materialTypeId = null;
            if(item != null && item.containsKey("materialTypeId")) {
              materialTypeId = item.getString("materialTypeId");
            }
            materialTypesStorageClient.get(materialTypeId,
              mtResponse -> {
              JsonObject materialTypeObject;
              if(mtResponse.getStatusCode() != 200) {
                materialTypeObject = null;
              } else {
                materialTypeObject = mtResponse.getJson();
              }
              JsonResponse.success(routingContext.response(),
                extendedLoan(loan, item, holding, instance,
                locationObject, materialTypeObject));    
            });       
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
    CollectionResourceClient loansStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

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
    CollectionResourceClient itemsClient;
    CollectionResourceClient holdingsClient;
    CollectionResourceClient instancesClient;
    CollectionResourceClient locationsClient;
    CollectionResourceClient materialTypesClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
      itemsClient = createItemsStorageClient(client, context);
      locationsClient = createLocationsStorageClient(client, context);
      holdingsClient = createHoldingsStorageClient(client, context);
      instancesClient = createInstanceStorageClient(client, context);
      materialTypesClient = createMaterialTypesStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

      return;
    }

    loansStorageClient.getMany(routingContext.request().query(), loansResponse -> {
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

        InventoryFetcher inventoryFetcher = new InventoryFetcher(itemsClient,
          holdingsClient, instancesClient);

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

          locationsClient.getMany(locationsQuery, locationIds.size(), 0,
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
                    possibleLocation.orElse(null),
                    null));
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
    CollectionResourceClient loansStorageClient;

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      loansStorageClient = createLoansStorageClient(client, context);
    }
    catch (MalformedURLException e) {
      reportInvalidOkapiUrlHeader(routingContext, context.getOkapiLocation());

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

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/loan-storage/loans"));
  }

  private CollectionResourceClient createItemsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/item-storage/items"));
  }

  private CollectionResourceClient createHoldingsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/holdings-storage/holdings"));
  }

  private CollectionResourceClient createInstanceStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/instance-storage/instances"));
  }

  private CollectionResourceClient createLocationsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/shelf-locations"));
  }

  private CollectionResourceClient createUsersStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient usersStorageClient;

    usersStorageClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/users"));

    return usersStorageClient;
  }
  
  private CollectionResourceClient createMaterialTypesStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {
    return new CollectionResourceClient(client,
      context.getOkapiBasedUrl("/material-types"));
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
    JsonObject location,
    JsonObject materialType) {
    JsonObject itemSummary = new JsonObject();

    final String titleProperty = "title";
    final String barcodeProperty = "barcode";
    final String statusProperty = "status";
    final String holdingsRecordIdProperty = "holdingsRecordId";
    final String instanceIdProperty = "instanceId";
    final String callNumberProperty = "callNumber";

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
    
    if(holding != null && holding.containsKey(callNumberProperty)) {
      itemSummary.put(callNumberProperty, holding.getString(callNumberProperty));
    }

    if(item.containsKey(statusProperty)) {
      itemSummary.put(statusProperty, item.getJsonObject(statusProperty));
    }

    if(location != null && location.containsKey("name")) {
      itemSummary.put("location", new JsonObject()
        .put("name", location.getString("name")));
    }
    
    if(materialType != null && materialType.containsKey("name")) {
      itemSummary.put("materialType", new JsonObject()
        .put("name", materialType.getString("name")));
    }

    return itemSummary;
  }

  private JsonObject extendedLoan(
    JsonObject loan,
    JsonObject item,
    JsonObject holding,
    JsonObject instance,
    JsonObject location,
    JsonObject materialType) {

    if(item != null) {
      loan.put("item", createItemSummary(item, instance, holding, location,
        materialType));
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
