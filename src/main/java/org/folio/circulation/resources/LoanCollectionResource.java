package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.*;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.validation.AlreadyCheckedOutValidator;
import org.folio.circulation.domain.validation.AwaitingPickupValidator;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.SuccessResponse;
import org.folio.circulation.support.http.server.WebContext;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

public class LoanCollectionResource extends CollectionResource {
  public LoanCollectionResource(HttpClient client) {
    super(client, "/circulation/loans");
  }

  void create(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject incomingRepresentation = routingContext.getBodyAsJson();

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
      clients, () -> failure(
        "proxyUserId is not valid", "proxyUserId", loan.getProxyUserId()));

    final AwaitingPickupValidator awaitingPickupValidator = new AwaitingPickupValidator(
      message -> failure(message, "userId", loan.getUserId()));

    final AlreadyCheckedOutValidator alreadyCheckedOutValidator = new AlreadyCheckedOutValidator(
      message -> failure(message, "itemId", loan.getItemId()));

    final ItemNotFoundValidator itemNotFoundValidator = createItemNotFoundValidator(loan);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    completedFuture(HttpResult.succeeded(new LoanAndRelatedRecords(loan)))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenCombineAsync(itemRepository.fetchFor(loan), this::addInventoryRecords)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenApply(this::refuseWhenHoldingDoesNotExist)
      .thenApply(alreadyCheckedOutValidator::refuseWhenItemIsAlreadyCheckedOut)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueFetcher.get(loan.getItemId()), this::addRequestQueue)
      .thenCombineAsync(userRepository.getUser(loan.getUserId()), this::addUser)
      .thenApply(awaitingPickupValidator::refuseWhenUserIsNotAwaitingPickup)
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

    final Loan loan = Loan.from(incomingRepresentation);

    final Clients clients = Clients.create(context, client);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, false, false);

    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> failure(
        "proxyUserId is not valid", "proxyUserId",
        loan.getProxyUserId()));

    final ItemNotFoundValidator itemNotFoundValidator = createItemNotFoundValidator(loan);

    completedFuture(HttpResult.succeeded(new LoanAndRelatedRecords(loan)))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenCombineAsync(itemRepository.fetchFor(loan), this::addInventoryRecords)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
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

        new OkJsonHttpResult(
          new MultipleRecordsWrapper(mappedLoans, "loans", loans.getTotalRecords())
            .toJson()).writeTo(routingContext.response());
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
        return HttpResult.failed(failure(
          "Holding does not exist", ITEM_ID, loan.getLoan().getItemId()));
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

  private ItemNotFoundValidator createItemNotFoundValidator(Loan loan) {
    return new ItemNotFoundValidator(
      () -> failure(String.format("No item with ID %s could be found", loan.getItemId()),
        ITEM_ID, loan.getItemId()));
  }
}
