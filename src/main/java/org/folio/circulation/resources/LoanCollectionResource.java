package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.isSameMillis;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.LoanService;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeService;
import org.folio.circulation.domain.notice.schedule.RequestScheduledNoticeService;
import org.folio.circulation.domain.validation.AlreadyCheckedOutValidator;
import org.folio.circulation.domain.validation.ChangeDueDateValidator;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ItemStatusValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.RequestedByAnotherPatronValidator;
import org.folio.circulation.domain.validation.ServicePointLoanLocationValidator;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.RequestQueueService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class LoanCollectionResource extends CollectionResource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public LoanCollectionResource(HttpClient client) {
    super(client, "/circulation/loans");
  }

  @Override
  void create(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject incomingRepresentation = routingContext.getBodyAsJson();

    final Loan loan = Loan.from(incomingRepresentation);

    final Clients clients = Clients.create(context, client);

    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var servicePointRepository = new ServicePointRepository(clients);
    final var requestRepository = RequestRepository.using(clients, itemRepository,
      userRepository, loanRepository);
    final var requestQueueRepository = new RequestQueueRepository(requestRepository);
    final var requestQueueUpdate = UpdateRequestQueue.using(clients,
      requestRepository, requestQueueRepository);
    final var requestQueueService = RequestQueueService.using(clients);
    final UpdateItem updateItem = new UpdateItem(itemRepository, requestQueueService);
    final LoanService loanService = new LoanService(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final EventPublisher eventPublisher = new EventPublisher(routingContext);
    final var requestScheduledNoticeService = RequestScheduledNoticeService.using(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator =
      new ProxyRelationshipValidator(clients,
        () -> singleValidationError("proxyUserId is not valid", "proxyUserId",
          loan.getProxyUserId()));

    final RequestedByAnotherPatronValidator requestedByAnotherPatronValidator = new RequestedByAnotherPatronValidator(
      message -> singleValidationError(message, "userId", loan.getUserId()), requestQueueService);

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
      .thenApply(alreadyCheckedOutValidator::refuseWhenItemIsAlreadyCheckedOut)
      .thenApply(itemStatusValidator::refuseWhenItemIsMissing)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueRepository.getByItemId(loan.getItemId()), this::addRequestQueue)
      .thenCombineAsync(userRepository.getUserFailOnNotFound(loan.getUserId()), this::addUser)
      .thenCompose(requestedByAnotherPatronValidator::refuseWhenRequestedByAnotherPatron)
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(requestScheduledNoticeService::rescheduleRequestNotices))
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
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients,
      itemRepository, userRepository);
    final var requestRepository = RequestRepository.using(clients,
      itemRepository, userRepository, loanRepository);
    final var requestQueueRepository = new RequestQueueRepository(requestRepository);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);

    final var requestQueueUpdate = UpdateRequestQueue.using(clients,
      requestRepository, requestQueueRepository);
    final UpdateItem updateItem = new UpdateItem(itemRepository, RequestQueueService.using(clients));

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> singleValidationError("proxyUserId is not valid", "proxyUserId",
        loan.getProxyUserId()));

    final ItemNotFoundValidator itemNotFoundValidator = createItemNotFoundValidator(loan);

    final ServicePointLoanLocationValidator spLoanLocationValidator =
        new ServicePointLoanLocationValidator();

    final ChangeDueDateValidator changeDueDateValidator = new ChangeDueDateValidator();

    final LoanScheduledNoticeService scheduledNoticeService
        = LoanScheduledNoticeService.using(clients);

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients, loanRepository);

    getExistingLoan(loanRepository , loan)
      .thenApply(e -> e.map(existingLoan -> new LoanAndRelatedRecords(loan, existingLoan)))
      .thenCompose(larrResult -> getServicePointsForLoanAndRelated(larrResult, servicePointRepository))
      .thenApply(this::refuseWhenNotOpenOrClosed)
      .thenApply(this::refuseWhenOpenAndNoUserId)
      .thenApply(spLoanLocationValidator::checkServicePointLoanLocation)
      .thenApply(this::refuseWhenClosedAndNoCheckInServicePointId)
      .thenCombineAsync(itemRepository.fetchFor(loan), this::addItem)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenCompose(changeDueDateValidator::refuseChangeDueDateForItemInDisallowedStatus)
      .thenCombineAsync(userRepository.getUser(loan.getUserId()), this::addUser)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueRepository.getByItemId(loan.getItemId()), this::addRequestQueue)
      .thenApply(r -> r.map(this::unsetDueDateChangedByRecallIfNoOpenRecallsInQueue))
      .thenComposeAsync(result -> result.after(requestQueueUpdate::onCheckIn))
      .thenComposeAsync(result -> result.after(updateItem::onLoanUpdate))
      // Loan must be updated after item
      // due to snapshot of item status stored with the loan
      // as this is how the loan action history is populated
      .thenComposeAsync(result -> result.after(loanRepository::updateLoan))
      .thenComposeAsync(r -> r.after(eventPublisher::publishDueDateChangedEvent))
      .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
      .thenCompose(r -> r.after(loanNoticeSender::sendManualDueDateChangeNotice))
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void get(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients,
      new ItemRepository(clients), userRepository);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
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
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void getMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final var userRepository = new UserRepository(clients);
    final var itemRepository = new ItemRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
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
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
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

  private Result<LoanAndRelatedRecords> refuseWhenClosedAndNoCheckInServicePointId(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    log.debug("refuseWhenClosedAndNoCheckInServicePointId:: parameters loanAndRelatedRecords: {}",
      () -> resultAsString(loanAndRelatedRecords));

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::closedLoanHasCheckInServicePointId)
      .next(v -> loanAndRelatedRecords);
  }

  private Result<LoanAndRelatedRecords> refuseWhenNotOpenOrClosed(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    log.debug("refuseWhenNotOpenOrClosed:: parameters loanAndRelatedRecords: {}",
      () -> resultAsString(loanAndRelatedRecords));

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::isValidStatus)
      .next(v -> loanAndRelatedRecords);
  }

  private Result<LoanAndRelatedRecords> refuseWhenOpenAndNoUserId(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    log.debug("refuseWhenOpenAndNoUserId:: parameters loanAndRelatedRecords: {}",
      () -> resultAsString(loanAndRelatedRecords));

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::openLoanHasUserId)
      .next(v -> loanAndRelatedRecords);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> getServicePointsForLoanAndRelated(
    Result<LoanAndRelatedRecords> larrResult,
    ServicePointRepository servicePointRepository) {

    log.debug("getServicePointsForLoanAndRelated:: parameters larrResult: {}",
      () -> resultAsString(larrResult));

    return larrResult.combineAfter(loanAndRelatedRecords ->
        getServicePointsForLoan(loanAndRelatedRecords.getLoan(), servicePointRepository),
      LoanAndRelatedRecords::withLoan);
  }

  private CompletableFuture<Result<Loan>> getServicePointsForLoan(
    Loan loan,
    ServicePointRepository servicePointRepository) {

    log.debug("getServicePointsForLoan:: parameters loan: {}", () -> loan);

    return servicePointRepository.findServicePointsForLoan(of(() -> loan));
  }

  private ItemNotFoundValidator createItemNotFoundValidator(Loan loan) {
    log.debug("createItemNotFoundValidator:: parameters loan: {}", () -> loan);

    return new ItemNotFoundValidator(
      () -> singleValidationError(
        String.format("No item with ID %s could be found", loan.getItemId()),
        ITEM_ID, loan.getItemId()));
  }

  private static ValidationErrorFailure errorWhenInIncorrectStatus(Item item) {
    log.debug("errorWhenInIncorrectStatus:: parameters item: {}", () -> item);
    String message =
      String.format("%s (%s) (Barcode: %s) has the item status %s, loan cannot be created",
        item.getTitle(),
        item.getMaterialTypeName(),
        item.getBarcode(),
        item.getStatusName());

    return singleValidationError(message, ITEM_ID, item.getItemId());
  }

  CompletableFuture<Result<Loan>> getExistingLoan(LoanRepository loanRepository, Loan loan) {
    log.debug("getExistingLoan:: parameters loan: {}", loan);

    return loanRepository.getById(loan.getId())
      .thenApplyAsync(r -> r.map(exitingLoan -> {
        exitingLoan.setPreviousDueDate(exitingLoan.getDueDate());
        loan.setPreviousDueDate(exitingLoan.getDueDate());
        return exitingLoan;
      }));
  }

  private LoanAndRelatedRecords unsetDueDateChangedByRecallIfNoOpenRecallsInQueue(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    log.debug("unsetDueDateChangedByRecallIfNoOpenRecallsInQueue:: " +
      "parameters loanAndRelatedRecords: {}", loanAndRelatedRecords);

    if (dueDateHasNotChanged(loanAndRelatedRecords.getExistingLoan(),
      loanAndRelatedRecords.getLoan())) {

      log.info("unsetDueDateChangedByRecallIfNoOpenRecallsInQueue:: due date has not changed");

      return loanAndRelatedRecords;
    }

    RequestQueue queue = loanAndRelatedRecords.getRequestQueue();
    Loan loan = loanAndRelatedRecords.getLoan();
    log.info("Loan {} prior to flag check: {}", loan.getId(), loan.asJson());
    if (loan.wasDueDateChangedByRecall() && !queue.hasOpenRecalls()) {
      log.info("Loan {} registers as having due date change flag set to true and no open recalls in queue.", loan.getId());
      return loanAndRelatedRecords.withLoan(loan.unsetDueDateChangedByRecall());
    } else {
      log.info("Loan {} registers as either not having due date change flag set to true or as having open recalls in queue.", loan.getId());
      return loanAndRelatedRecords;
    }
  }

  private boolean dueDateHasNotChanged(Loan existingLoan, Loan changedLoan) {
    return existingLoan == null || isSameMillis(existingLoan.getDueDate(), changedLoan.getDueDate());
  }
}
