package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.*;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.LoanValidation.defaultStatusAndAction;

public class LoanCollectionResource extends CollectionResource {
  private static final String MT_ID_PROPERTY = "materialTypeId";

  public LoanCollectionResource(HttpClient client) {
    super(client, "/circulation/loans");
  }
  
  void create(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject loan = routingContext.getBodyAsJson();
    defaultStatusAndAction(loan);

    final Clients clients = Clients.create(context, client);

    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final UserFetcher userFetcher = new UserFetcher(clients);

    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final MaterialTypeRepository materialTypeRepository = new MaterialTypeRepository(clients);
    final LocationRepository locationRepository = new LocationRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> new ValidationErrorFailure(
        "proxyUserId is not valid", "proxyUserId",
        loan.getString("proxyUserId")));

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    final String itemId = loan.getString("itemId");
    final String requestingUserId = loan.getString("userId");

    completedFuture(HttpResult.success(new LoanAndRelatedRecords(Loan.from(loan))))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenCombineAsync(inventoryFetcher.fetch(loan), this::addInventoryRecords)
      .thenApply(LoanValidation::refuseWhenItemDoesNotExist)
      .thenApply(this::refuseWhenHoldingDoesNotExist)
      .thenApply(LoanValidation::refuseWhenItemIsAlreadyCheckedOut)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueFetcher.get(itemId), this::addRequestQueue)
      .thenCombineAsync(userFetcher.getUser(requestingUserId), this::addUser)
      .thenApply(LoanValidation::refuseWhenUserIsNotAwaitingPickup)
      .thenComposeAsync(r -> r.after(locationRepository::getLocation))
      .thenComposeAsync(r -> r.after(materialTypeRepository::getMaterialType))
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onCheckOut))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(CreatedJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void replace(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject loan = routingContext.getBodyAsJson();

    loan.put("id", routingContext.request().getParam("id"));

    defaultStatusAndAction(loan);

    final Clients clients = Clients.create(context, client);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);

    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> new ValidationErrorFailure(
        "proxyUserId is not valid", "proxyUserId",
        loan.getString("proxyUserId")));

    String itemId = loan.getString("itemId");

    completedFuture(HttpResult.success(new LoanAndRelatedRecords(Loan.from(loan))))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenCombineAsync(inventoryFetcher.fetch(loan), this::addInventoryRecords)
      .thenApply(LoanValidation::refuseWhenItemDoesNotExist)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueFetcher.get(itemId), this::addRequestQueue)
      .thenComposeAsync(result -> result.after(requestQueueUpdate::onCheckIn))
      .thenComposeAsync(result -> result.after(updateItem::onLoanUpdate))
      .thenComposeAsync(result -> result.after(loanRepository::updateLoan))
      .thenApply(NoContentHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void get(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final MaterialTypeRepository materialTypeRepository = new MaterialTypeRepository(clients);
    final LocationRepository locationRepository = new LocationRepository(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    String id = routingContext.request().getParam("id");

    loanRepository.getById(id)
      .thenComposeAsync(result -> result.after(inventoryFetcher::getInventoryRecords))
      .thenComposeAsync(r -> r.after(locationRepository::getLocation))
      .thenComposeAsync(r -> r.after(materialTypeRepository::getMaterialType))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(OkJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

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

  void getMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    clients.loansStorage().getMany(routingContext.request().query(), loansResponse -> {
      if (loansResponse.getStatusCode() != 200) {
        ServerErrorResponse.internalError(routingContext.response(),
          "Failed to fetch loans from storage");
        return;
      }

      final MultipleRecordsWrapper wrappedLoans = MultipleRecordsWrapper.fromBody(
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
          .map(item -> LoanValidation.determineLocationIdForItem(item,
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

          //Also get a list of material types
          List<String> materialTypeIds = records.getItems().stream()
            .map(item -> item.getString(MT_ID_PROPERTY))
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
          CompletableFuture<Response> materialTypesFetched = new CompletableFuture<>();
          String materialTypesQuery = CqlHelper.multipleRecordsCqlQuery(materialTypeIds);

          clients.materialTypesStorage().getMany(materialTypesQuery,
            materialTypeIds.size(), 0, materialTypesFetched::complete);

          materialTypesFetched.thenAccept(materialTypesResponse -> {
            if(materialTypesResponse.getStatusCode() != 200) {
              ServerErrorResponse.internalError(routingContext.response(),
                String.format("Material Types request (%s) failed %s: %s",
                  materialTypesQuery, materialTypesResponse.getStatusCode(),
                  materialTypesResponse.getBody()));
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
              String[] materialTypeId = new String[]{null};
              if(possibleItem.isPresent()) {
                JsonObject item = possibleItem.get();
                materialTypeId[0] = item.getString(MT_ID_PROPERTY);

                Optional<JsonObject> possibleHolding = records.findHoldingById(
                  item.getString("holdingsRecordId"));

                if(possibleHolding.isPresent()) {
                  JsonObject holding = possibleHolding.get();

                  possibleInstance = records.findInstanceById(
                    holding.getString("instanceId"));
                }

                List<JsonObject> locations = JsonArrayHelper.toList(
                  locationsResponse.getJson().getJsonArray("locations"));

                Optional<JsonObject> possibleLocation = locations.stream()
                  .filter(location -> location.getString("id").equals(
                    LoanValidation.determineLocationIdForItem(item, possibleHolding.orElse(null))))
                  .findFirst();

                List<JsonObject> materialTypes = JsonArrayHelper.toList(
                  materialTypesResponse.getJson().getJsonArray("mtypes"));


                Optional<JsonObject> possibleMaterialType = materialTypes.stream()
                  .filter(materialType -> materialType.getString("id")
                  .equals(materialTypeId[0])).findFirst();

                loan.put("item", loanRepresentation.createItemSummary(item,
                  possibleInstance.orElse(null),
                    possibleHolding.orElse(null),
                    possibleLocation.orElse(null),
                    possibleMaterialType.orElse(null)));
              }
            });

            JsonResponse.success(routingContext.response(),
              wrappedLoans.toJson());

          });
        });
      });
    });
  }

  void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.loansStorage().delete(response -> {
      if(response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
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
    HttpResult<User> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withRequestingUser);
  }

  private HttpResult<LoanAndRelatedRecords> refuseWhenHoldingDoesNotExist(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      if(loan.inventoryRecords.getHolding() == null) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Holding does not exist", "itemId", loan.loan.getItemId()));
      }
      else {
        return result;
      }
    });
  }

  private HttpResult<LoanAndRelatedRecords> refuseWhenNotOpenOrClosed(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .map(r -> r.loan)
      .next(Loan::isValidStatus)
      .next(v -> loanAndRelatedRecords);
  }
}
