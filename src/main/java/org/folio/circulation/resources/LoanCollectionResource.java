package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.*;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.JsonResponse;
import org.folio.circulation.support.http.server.SuccessResponse;
import org.folio.circulation.support.http.server.WebContext;

import java.util.List;
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

    final ItemRepository itemRepository = new ItemRepository(clients, true, true);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final UserRepository userRepository = new UserRepository(clients);

    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> ValidationErrorFailure.failure(
        "proxyUserId is not valid", "proxyUserId",
        loan.getProxyUserId()));

    final AwaitingPickupValidator awaitingPickupValidator = new AwaitingPickupValidator();

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    completedFuture(HttpResult.success(new LoanAndRelatedRecords(loan)))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenCombineAsync(itemRepository.fetchFor(loan), this::addInventoryRecords)
      .thenApply(LoanValidation::refuseWhenItemDoesNotExist)
      .thenApply(this::refuseWhenHoldingDoesNotExist)
      .thenApply(LoanValidation::refuseWhenItemIsAlreadyCheckedOut)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueFetcher.get(loan.getItemId()), this::addRequestQueue)
      .thenCombineAsync(userRepository.getUser(loan.getUserId()), this::addUser)
      .thenApply(loanAndRelatedRecords -> awaitingPickupValidator.refuseWhenUserIsNotAwaitingPickup(loanAndRelatedRecords))
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onCheckOut))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenApply(r -> r.map(LoanAndRelatedRecords::getLoan))
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
    final ItemRepository itemRepository = new ItemRepository(clients, false, false);

    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> ValidationErrorFailure.failure(
        "proxyUserId is not valid", "proxyUserId",
        loan.getProxyUserId()));

    completedFuture(HttpResult.success(new LoanAndRelatedRecords(loan)))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenCombineAsync(itemRepository.fetchFor(loan), this::addInventoryRecords)
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

    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    String id = routingContext.request().getParam("id");

    loanRepository.getById(id)
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

    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    loanRepository.findBy(routingContext.request().query())
      .thenAccept(result -> {
        if(result.failed()) {
          result.cause().writeTo(routingContext.response());
        }

        final MultipleRecords<Loan> loans = result.value();

        final List<JsonObject> mappedLoans = loans.getRecords().stream()
          .map(loanRepresentation::extendedLoan)
          .collect(Collectors.toList());

          JsonResponse.success(routingContext.response(),
            new MultipleRecordsWrapper(mappedLoans, "loans", loans.getTotalRecords())
              .toJson());
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
    HttpResult<Item> inventoryRecordsResult) {

    return HttpResult.combine(loanResult, inventoryRecordsResult,
      LoanAndRelatedRecords::withItem);
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
      if(loan.getLoan().getItem().doesNotHaveHolding()) {
        return HttpResult.failure(ValidationErrorFailure.failure(
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
