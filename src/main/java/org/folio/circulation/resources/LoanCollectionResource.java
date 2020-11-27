package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.LoanService;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.domain.notice.schedule.DueDateScheduledNoticeService;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.domain.validation.AlreadyCheckedOutValidator;
import org.folio.circulation.domain.validation.ChangeDueDateValidator;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ItemStatusValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.RequestedByAnotherPatronValidator;
import org.folio.circulation.domain.validation.ServicePointLoanLocationValidator;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class LoanCollectionResource extends CollectionResource {
  public LoanCollectionResource(HttpClient client) {
    super(client, "/circulation/loans");
  }

  @Override
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
    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final ProxyRelationshipValidator proxyRelationshipValidator =
      new ProxyRelationshipValidator(clients,
        () -> singleValidationError("proxyUserId is not valid", "proxyUserId",
          loan.getProxyUserId()));

    final RequestedByAnotherPatronValidator requestedByAnotherPatronValidator = new RequestedByAnotherPatronValidator(
      message -> singleValidationError(message, "userId", loan.getUserId()));

    final AlreadyCheckedOutValidator alreadyCheckedOutValidator = new AlreadyCheckedOutValidator(
      message -> singleValidationError(message, "itemId", loan.getItemId()));

    final ItemStatusValidator itemStatusValidator = new ItemStatusValidator(
      LoanCollectionResource::errorWhenInIncorrectStatus);

    final ItemNotFoundValidator itemNotFoundValidator = createItemNotFoundValidator(loan);

    final ServicePointLoanLocationValidator spLoanLocationValidator =
        new ServicePointLoanLocationValidator();

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    completedFuture(succeeded(new LoanAndRelatedRecords(loan)))
      .thenCompose(larrResult ->
        getServicePointsForLoanAndRelated(larrResult, servicePointRepository))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenApply(this::refuseWhenOpenAndNoUserId)
      .thenApply(spLoanLocationValidator::checkServicePointLoanLocation)
      .thenCombineAsync(itemRepository.fetchFor(loan), this::addItem)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenApply(this::refuseWhenHoldingDoesNotExist)
      .thenApply(alreadyCheckedOutValidator::refuseWhenItemIsAlreadyCheckedOut)
      .thenApply(itemStatusValidator::refuseWhenItemIsMissing)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueRepository.get(loan.getItemId()), this::addRequestQueue)
      .thenCombineAsync(userRepository.getUserFailOnNotFound(loan.getUserId()), this::addUser)
      .thenApply(requestedByAnotherPatronValidator::refuseWhenRequestedByAnotherPatron)
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onLoanCreated))
      .thenComposeAsync(r -> r.after(loanService::truncateLoanWhenItemRecalled))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenComposeAsync(r -> r.after(eventPublisher::publishDueDateChangedEvent))
      .thenApply(r -> r.map(LoanAndRelatedRecords::getLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(r -> r.map(JsonHttpResponse::created))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void replace(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject incomingRepresentation = routingContext.getBodyAsJson();

    incomingRepresentation.put("id", routingContext.request().getParam("id"));

    final Loan loan = Loan.from(incomingRepresentation);

    final Clients clients = Clients.create(context, client);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final UserRepository userRepository = new UserRepository(clients);

    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> singleValidationError("proxyUserId is not valid", "proxyUserId",
        loan.getProxyUserId()));

    final ItemNotFoundValidator itemNotFoundValidator = createItemNotFoundValidator(loan);

    final ServicePointLoanLocationValidator spLoanLocationValidator =
        new ServicePointLoanLocationValidator();

    final ChangeDueDateValidator changeDueDateValidator
        = new ChangeDueDateValidator(loanRepository);

    final DueDateScheduledNoticeService scheduledNoticeService
        = DueDateScheduledNoticeService.using(clients);

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients);

    completedFuture(succeeded(new LoanAndRelatedRecords(loan)))
      .thenCompose(larrResult ->
        getServicePointsForLoanAndRelated(larrResult, servicePointRepository))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenApply(this::refuseWhenOpenAndNoUserId)
      .thenApply(spLoanLocationValidator::checkServicePointLoanLocation)
      .thenApply(this::refuseWhenClosedAndNoCheckInServicePointId)
      .thenCombineAsync(itemRepository.fetchFor(loan), this::addItem)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenCompose(changeDueDateValidator::refuseChangeDueDateForItemInDisallowedStatus)
      .thenCombineAsync(userRepository.getUser(loan.getUserId()), this::addUser)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueRepository.get(loan.getItemId()), this::addRequestQueue)
      .thenComposeAsync(result -> result.after(requestQueueUpdate::onCheckIn))
      .thenComposeAsync(result -> result.after(updateItem::onLoanUpdate))
      // Loan must be updated after item
      // due to snapshot of item status stored with the loan
      // as this is how the loan action history is populated
      .thenComposeAsync(result -> result.after(loanRepository::updateLoan))
      .thenComposeAsync(r -> r.after(eventPublisher::publishDueDateChangedEvent))
      .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
      .thenCompose(r -> r.after(loanNoticeSender::sendManualDueDateChangeNotice))
      .thenApply(r -> r.toFixedValue(NoContentResponse::noContent))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void get(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final UserRepository userRepository = new UserRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final OverdueFinePolicyRepository overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
    final LostItemPolicyRepository lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    final AccountRepository accountRepository = new AccountRepository(clients);
    final PatronGroupRepository patronGroupRepository = new PatronGroupRepository(clients);

    String id = routingContext.request().getParam("id");

    loanRepository.getById(id)
      .thenComposeAsync(accountRepository::findAccountsAndActionsForLoan)
      .thenComposeAsync(servicePointRepository::findServicePointsForLoan)
      .thenComposeAsync(userRepository::findUserForLoan)
      .thenComposeAsync(loanPolicyRepository::findPolicyForLoan)
      .thenComposeAsync(overdueFinePolicyRepository::findOverdueFinePolicyForLoan)
      .thenComposeAsync(lostItemPolicyRepository::findLostItemPolicyForLoan)
      .thenComposeAsync(patronGroupRepository::findGroupForLoan)
      .thenApply(loanResult -> loanResult.map(loanRepresentation::extendedLoan))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    String id = routingContext.request().getParam("id");

    clients.loansStorage().delete(id)
      .thenApply(r -> r.toFixedValue(NoContentResponse::noContent))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void getMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final UserRepository userRepository = new UserRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final OverdueFinePolicyRepository overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
    final LostItemPolicyRepository lostItemPolicyRepository = new LostItemPolicyRepository(clients);
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
        multiLoanRecordsResult.after(overdueFinePolicyRepository::findOverdueFinePoliciesForLoans))
      .thenCompose(multiLoanRecordsResult ->
        multiLoanRecordsResult.after(lostItemPolicyRepository::findLostItemPoliciesForLoans))
      .thenCompose(multiLoanRecordsResult ->
        multiLoanRecordsResult.after(patronGroupRepository::findPatronGroupsByIds))
      .thenApply(multipleLoanRecordsResult -> multipleLoanRecordsResult.map(loans ->
        loans.asJson(loanRepresentation::extendedLoan, "loans")))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.loansStorage().delete()
      .thenApply(r -> r.toFixedValue(NoContentResponse::noContent))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private Result<LoanAndRelatedRecords> addItem(Result<LoanAndRelatedRecords> loanResult,
    Result<Item> item) {

    return loanResult.combine(item, LoanAndRelatedRecords::withItem);
  }

  private Result<LoanAndRelatedRecords> addRequestQueue(Result<LoanAndRelatedRecords> loanResult,
    Result<RequestQueue> requestQueueResult) {

    return loanResult.combine(requestQueueResult, LoanAndRelatedRecords::withRequestQueue);
  }

  private Result<LoanAndRelatedRecords> addUser(Result<LoanAndRelatedRecords> loanResult,
    Result<User> getUserResult) {

    return loanResult.combine(getUserResult, LoanAndRelatedRecords::withRequestingUser);
  }

  private Result<LoanAndRelatedRecords> refuseWhenHoldingDoesNotExist(
    Result<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      if(loan.getLoan().getItem().doesNotHaveHolding()) {
        return failedValidation("Holding does not exist",
          ITEM_ID, loan.getLoan().getItemId());
      }
      else {
        return result;
      }
    });
  }

  private Result<LoanAndRelatedRecords> refuseWhenClosedAndNoCheckInServicePointId(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::closedLoanHasCheckInServicePointId)
      .next(v -> loanAndRelatedRecords);
  }

  private Result<LoanAndRelatedRecords> refuseWhenNotOpenOrClosed(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::isValidStatus)
      .next(v -> loanAndRelatedRecords);
  }

  private Result<LoanAndRelatedRecords> refuseWhenOpenAndNoUserId(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::openLoanHasUserId)
      .next(v -> loanAndRelatedRecords);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> getServicePointsForLoanAndRelated(
    Result<LoanAndRelatedRecords> larrResult,
    ServicePointRepository servicePointRepository) {

    return larrResult.combineAfter(loanAndRelatedRecords ->
        getServicePointsForLoan(loanAndRelatedRecords.getLoan(), servicePointRepository),
      LoanAndRelatedRecords::withLoan);
  }

  private CompletableFuture<Result<Loan>> getServicePointsForLoan(
    Loan loan,
    ServicePointRepository servicePointRepository) {

    return servicePointRepository.findServicePointsForLoan(of(() -> loan));
  }

  private ItemNotFoundValidator createItemNotFoundValidator(Loan loan) {
    return new ItemNotFoundValidator(
      () -> singleValidationError(
        String.format("No item with ID %s could be found", loan.getItemId()),
        ITEM_ID, loan.getItemId()));
  }

  private static ValidationErrorFailure errorWhenInIncorrectStatus(Item item) {
    String message =
      String.format("%s (%s) (Barcode:%s) has the item status %s, loan cannot be created",
        item.getTitle(),
        item.getMaterialTypeName(),
        item.getBarcode(),
        item.getStatusName());

    return singleValidationError(message, ITEM_ID, item.getItemId());
  }
}
