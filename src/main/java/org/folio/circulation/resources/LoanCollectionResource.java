package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.*;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.LoanValidation.defaultStatusAndAction;

public class LoanCollectionResource extends CollectionResource {
  public LoanCollectionResource(HttpClient client) {
    super(client, "/circulation/loans");
  }

  void create(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject incomingRepresentation = routingContext.getBodyAsJson();
    defaultStatusAndAction(incomingRepresentation);

    final Loan loan = Loan.from(incomingRepresentation);

    final Clients clients = Clients.create(context, client);

    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients, true);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final UserFetcher userFetcher = new UserFetcher(clients);

    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final MaterialTypeRepository materialTypeRepository = new MaterialTypeRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> new ValidationErrorFailure(
        "proxyUserId is not valid", "proxyUserId",
        loan.getProxyUserId()));

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    completedFuture(HttpResult.success(new LoanAndRelatedRecords(loan)))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenCombineAsync(inventoryFetcher.fetchFor(loan), this::addInventoryRecords)
      .thenApply(LoanValidation::refuseWhenItemDoesNotExist)
      .thenApply(this::refuseWhenHoldingDoesNotExist)
      .thenApply(LoanValidation::refuseWhenItemIsAlreadyCheckedOut)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueFetcher.get(loan.getItemId()), this::addRequestQueue)
      .thenCombineAsync(userFetcher.getUser(loan.getUserId()), this::addUser)
      .thenApply(LoanValidation::refuseWhenUserIsNotAwaitingPickup)
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

    JsonObject incomingRepresentation = routingContext.getBodyAsJson();

    incomingRepresentation.put("id", routingContext.request().getParam("id"));

    defaultStatusAndAction(incomingRepresentation);

    final Loan loan = Loan.from(incomingRepresentation);

    final Clients clients = Clients.create(context, client);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients, false);

    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> new ValidationErrorFailure(
        "proxyUserId is not valid", "proxyUserId",
        loan.getProxyUserId()));

    completedFuture(HttpResult.success(new LoanAndRelatedRecords(loan)))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenCombineAsync(inventoryFetcher.fetchFor(loan), this::addInventoryRecords)
      .thenApply(LoanValidation::refuseWhenItemDoesNotExist)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueFetcher.get(loan.getItemId()), this::addRequestQueue)
      .thenComposeAsync(result -> result.after(requestQueueUpdate::onCheckIn))
      .thenComposeAsync(result -> result.after(updateItem::onLoanUpdate))
      .thenComposeAsync(result -> result.after(loanRepository::updateLoan))
      .thenApply(NoContentHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void get(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients, true);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final MaterialTypeRepository materialTypeRepository = new MaterialTypeRepository(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    String id = routingContext.request().getParam("id");

    loanRepository.getById(id)
      .thenComposeAsync(result -> result.after(inventoryFetcher::getInventoryRecords))
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

    final LocationRepository locationRepository = new LocationRepository(clients);

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
        .map(loan -> loan.getString(LoanProperties.ITEM_ID))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

      InventoryFetcher inventoryFetcher = new InventoryFetcher(clients, true);

      CompletableFuture<MultipleInventoryRecords> inventoryRecordsFetched =
        inventoryFetcher.fetchFor(itemIds, e ->
          ServerErrorResponse.internalError(routingContext.response(), e.toString()));

      inventoryRecordsFetched.thenAccept(records -> {
        //Also get a list of material types
        List<String> materialTypeIds = records.getRecords().stream()
          .map(InventoryRecords::getMaterialTypeId)
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
            //No need to pass on the itemStatus property,
            // as only used to populate the history
            //and could be confused with aggregation of current status
            loan.remove("itemStatus");

            final InventoryRecords record = records.findRecordByItemId(
              loan.getString(LoanProperties.ITEM_ID));

            if(record.isFound()) {
              List<JsonObject> materialTypes = JsonArrayHelper.toList(
                materialTypesResponse.getJson().getJsonArray("mtypes"));

              Optional<JsonObject> possibleMaterialType = materialTypes.stream()
                .filter(materialType -> materialType.getString("id")
                .equals(record.getMaterialTypeId())).findFirst();

              record.setMaterialType(possibleMaterialType.orElse(null));

              loan.put("item", loanRepresentation.createItemSummary(record));
            }
          });

          JsonResponse.success(routingContext.response(),
            wrappedLoans.toJson());
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
      if(loan.getLoan().getInventoryRecords().doesNotHaveHolding()) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Holding does not exist", LoanProperties.ITEM_ID, loan.getLoan().getItemId()));
      }
      else {
        return result;
      }
    });
  }

  private HttpResult<LoanAndRelatedRecords> refuseWhenNotOpenOrClosed(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::isValidStatus)
      .next(v -> loanAndRelatedRecords);
  }
}
