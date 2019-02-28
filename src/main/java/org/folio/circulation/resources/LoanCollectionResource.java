package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.validation.AlreadyCheckedOutValidator;
import org.folio.circulation.domain.validation.AwaitingPickupValidator;
import org.folio.circulation.domain.validation.ItemMissingValidator;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointLoanLocationValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonHttpResult;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.NoContentHttpResult;
import org.folio.circulation.support.OkJsonHttpResult;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

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
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final UserRepository userRepository = new UserRepository(clients);

    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator =
      new ProxyRelationshipValidator(clients,
        () -> failure("proxyUserId is not valid", "proxyUserId", loan.getProxyUserId()));

    final AwaitingPickupValidator awaitingPickupValidator = new AwaitingPickupValidator(
      message -> failure(message, "userId", loan.getUserId()));

    final AlreadyCheckedOutValidator alreadyCheckedOutValidator = new AlreadyCheckedOutValidator(
      message -> failure(message, "itemId", loan.getItemId()));

    final ItemMissingValidator itemMissingValidator = new ItemMissingValidator(
      message -> failure(message, "itemId", loan.getItemId()));

    final ItemNotFoundValidator itemNotFoundValidator = createItemNotFoundValidator(loan);

    final ServicePointLoanLocationValidator spLoanLocationValidator =
        new ServicePointLoanLocationValidator();

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    completedFuture(HttpResult.succeeded(new LoanAndRelatedRecords(loan)))
      .thenCompose(larrResult ->
        getServicePointsForLoanAndRelated(larrResult, servicePointRepository))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenApply(this::refuseWhenOpenAndNoUserId)
      .thenApply(spLoanLocationValidator::checkServicePointLoanLocation)
      .thenCombineAsync(itemRepository.fetchFor(loan), this::addItem)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenApply(this::refuseWhenHoldingDoesNotExist)
      .thenApply(alreadyCheckedOutValidator::refuseWhenItemIsAlreadyCheckedOut)
      .thenApply(itemMissingValidator::refuseWhenItemIsMissing)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueRepository.get(loan.getItemId()), this::addRequestQueue)
      .thenCombineAsync(userRepository.getUserFailOnNotFound(loan.getUserId()), this::addUser)
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
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, false, false);

    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> failure("proxyUserId is not valid", "proxyUserId",
        loan.getProxyUserId()));

    final ItemNotFoundValidator itemNotFoundValidator = createItemNotFoundValidator(loan);

    final ServicePointLoanLocationValidator spLoanLocationValidator =
        new ServicePointLoanLocationValidator();

    completedFuture(HttpResult.succeeded(new LoanAndRelatedRecords(loan)))
      .thenCompose(larrResult ->
        getServicePointsForLoanAndRelated(larrResult, servicePointRepository))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenApply(this::refuseWhenOpenAndNoUserId)
      .thenApply(spLoanLocationValidator::checkServicePointLoanLocation)
      .thenApply(this::refuseWhenClosedAndNoCheckInServicePointId)
      .thenCombineAsync(itemRepository.fetchFor(loan), this::addItem)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueRepository.get(loan.getItemId()), this::addRequestQueue)
      .thenComposeAsync(result -> result.after(requestQueueUpdate::onCheckIn))
      .thenComposeAsync(result -> result.after(updateItem::onLoanUpdate))
      // Loan must be updated after item
      // due to snapshot of item status stored with the loan
      // as this is how the loan action history is populated
      .thenComposeAsync(result -> result.after(loanRepository::updateLoan))
      .thenApply(NoContentHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void get(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    String id = routingContext.request().getParam("id");

    loanRepository.getById(id)
      .thenComposeAsync(servicePointRepository::findServicePointsForLoan)
      .thenApply(loanResult -> loanResult.map(loanRepresentation::extendedLoan))
      .thenApply(OkJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    String id = routingContext.request().getParam("id");

    clients.loansStorage().delete(id)
      .thenApply(NoContentHttpResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  void getMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    loanRepository.findBy(routingContext.request().query())
      .thenCompose(multiLoanRecordsResult ->
        multiLoanRecordsResult.after(servicePointRepository::findServicePointsForLoans))
      .thenApply(multipleLoanRecordsResult -> multipleLoanRecordsResult.map(loans ->
        loans.asJson(loanRepresentation::extendedLoan, "loans")))
      .thenApply(OkJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.loansStorage().delete()
      .thenApply(NoContentHttpResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private HttpResult<LoanAndRelatedRecords> addItem(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<Item> item) {

    return HttpResult.combine(loanResult, item,
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

  private HttpResult<LoanAndRelatedRecords> refuseWhenClosedAndNoCheckInServicePointId(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::closedLoanHasCheckInServicePointId)
      .next(v -> loanAndRelatedRecords);
  }

  private HttpResult<LoanAndRelatedRecords> refuseWhenNotOpenOrClosed(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::isValidStatus)
      .next(v -> loanAndRelatedRecords);
  }

  private HttpResult<LoanAndRelatedRecords> refuseWhenOpenAndNoUserId(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::openLoanHasUserId)
      .next(v -> loanAndRelatedRecords);
  }

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> getServicePointsForLoanAndRelated(
    HttpResult<LoanAndRelatedRecords> larrResult,
    ServicePointRepository servicePointRepository) {

    return larrResult.combineAfter(loanAndRelatedRecords ->
        getServicePointsForLoan(loanAndRelatedRecords.getLoan(), servicePointRepository),
      LoanAndRelatedRecords::withLoan);
  }

  private CompletableFuture<HttpResult<Loan>> getServicePointsForLoan(
    Loan loan,
    ServicePointRepository servicePointRepository) {

    return servicePointRepository.findServicePointsForLoan(HttpResult.of(() -> loan));
  }

  private ItemNotFoundValidator createItemNotFoundValidator(Loan loan) {
    return new ItemNotFoundValidator(
      () -> failure(String.format("No item with ID %s could be found", loan.getItemId()),
        ITEM_ID, loan.getItemId()));
  }
}
