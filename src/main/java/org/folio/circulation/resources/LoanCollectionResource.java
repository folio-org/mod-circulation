package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.resources.LoanCollectionResourceHelper.createItemNotFoundValidator;
import static org.folio.circulation.resources.LoanCollectionResourceHelper.getServicePointsForLoanAndRelated;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.LoanService;
import org.folio.circulation.domain.PatronGroupRepository;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.validation.AlreadyCheckedOutValidator;
import org.folio.circulation.domain.validation.HoldingsValidator;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ItemStatusValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.RequestedByAnotherPatronValidator;
import org.folio.circulation.domain.validation.ServicePointLoanLocationValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.OkJsonResponseResult;
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

    final ItemRepository itemRepository = new ItemRepository(clients, true, true, false);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final UserRepository userRepository = new UserRepository(clients);

    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanService loanService = new LoanService(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator =
      new ProxyRelationshipValidator(clients,
        () -> singleValidationError("proxyUserId is not valid", "proxyUserId",
          loan.getProxyUserId()));

    final RequestedByAnotherPatronValidator requestedByAnotherPatronValidator = new RequestedByAnotherPatronValidator(
      message -> singleValidationError(message, "userId", loan.getUserId()));

    final AlreadyCheckedOutValidator alreadyCheckedOutValidator = new AlreadyCheckedOutValidator(
      message -> singleValidationError(message, "itemId", loan.getItemId()));

    final ItemStatusValidator itemStatusValidator = new ItemStatusValidator(
      message -> singleValidationError(message, "itemId", loan.getItemId()));

    final ItemNotFoundValidator itemNotFoundValidator = createItemNotFoundValidator(loan);

    final ServicePointLoanLocationValidator spLoanLocationValidator =
        new ServicePointLoanLocationValidator();

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    completedFuture(succeeded(new LoanAndRelatedRecords(loan)))
      .thenCompose(larrResult ->
        getServicePointsForLoanAndRelated(larrResult, servicePointRepository))
      .thenApply(LoanCollectionResourceHelper::refuseWhenNotOpenOrClosed)
      .thenApply(LoanCollectionResourceHelper::refuseWhenOpenAndNoUserId)
      .thenApply(spLoanLocationValidator::checkServicePointLoanLocation)
      .thenCombineAsync(itemRepository.fetchFor(loan), LoanCollectionResourceHelper::addItem)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenApply(HoldingsValidator::refuseWhenHoldingDoesNotExist)
      .thenApply(alreadyCheckedOutValidator::refuseWhenItemIsAlreadyCheckedOut)
      .thenApply(itemStatusValidator::refuseWhenItemStatusIsInvalid)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueRepository.get(loan.getItemId()), LoanCollectionResourceHelper::addRequestQueue)
      .thenCombineAsync(userRepository.getUserFailOnNotFound(loan.getUserId()), LoanCollectionResourceHelper::addUser)
      .thenApply(requestedByAnotherPatronValidator::refuseWhenRequestedByAnotherPatron)
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onCheckOut))
      .thenComposeAsync(r -> r.after(loanService::truncateLoanWhenItemRecalled))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenApply(r -> r.map(LoanAndRelatedRecords::getLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(CreatedJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void replace(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject incomingRepresentation = routingContext.getBodyAsJson();

    incomingRepresentation.put("id", routingContext.request().getParam("id"));

    final Loan loan = Loan.from(incomingRepresentation);

    final Clients clients = Clients.create(context, client);

    LoanRepository loanRepository = new LoanRepository(clients);
    loanRepository
      .replaceLoan(loan)
      .thenApply(NoContentResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void get(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final UserRepository userRepository = new UserRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final AccountRepository accountRepository = new AccountRepository(clients);
    final PatronGroupRepository patronGroupRepository = new PatronGroupRepository(clients);

    String id = routingContext.request().getParam("id");

    loanRepository.getById(id)
      .thenComposeAsync(accountRepository::findAccountsForLoan)
      .thenComposeAsync(servicePointRepository::findServicePointsForLoan)
      .thenComposeAsync(userRepository::findUserForLoan)
      .thenComposeAsync(loanPolicyRepository::findPolicyForLoan)
      .thenComposeAsync(patronGroupRepository::findGroupForLoan)
      .thenApply(loanResult -> loanResult.map(loanRepresentation::extendedLoan))
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    String id = routingContext.request().getParam("id");

    clients.loansStorage().delete(id)
      .thenApply(NoContentResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  void getMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final UserRepository userRepository = new UserRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final AccountRepository accountRepository = new AccountRepository(clients);
    final PatronGroupRepository patronGroupRepository = new PatronGroupRepository(clients);

    loanRepository.findBy(routingContext.request().query())
      .thenCompose(multiLoanRecordsResult ->
        multiLoanRecordsResult.after(accountRepository::findAccountsForLoans))
      .thenCompose(multiLoanRecordsResult ->
        multiLoanRecordsResult.after(servicePointRepository::findServicePointsForLoans))
      .thenCompose(multiLoanRecordsResult ->
        multiLoanRecordsResult.after(userRepository::findUsersForLoans))
      .thenCompose(multiLoanRecordsResult ->
        multiLoanRecordsResult.after(loanPolicyRepository::findLoanPoliciesForLoans))
      .thenCompose(multiLoanRecordsResult ->
        multiLoanRecordsResult.after(patronGroupRepository::findPatronGroupsByIds))
      .thenApply(multipleLoanRecordsResult -> multipleLoanRecordsResult.map(loans ->
        loans.asJson(loanRepresentation::extendedLoan, "loans")))
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.loansStorage().delete()
      .thenApply(NoContentResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }
}
