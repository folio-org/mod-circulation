package org.folio.circulation.resources;

import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_ITEM;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_PROXY_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_REQUEST_QUEUE;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_USER;
import static org.folio.circulation.support.http.server.JsonHttpResponse.created;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.LoanService;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeService;
import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.domain.validation.CheckOutValidators;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.DeferFailureErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CheckOutByBarcodeResource extends Resource {

  private final String rootPath;
  private final CheckOutStrategy checkOutStrategy;

  public CheckOutByBarcodeResource(String rootPath, HttpClient client, CheckOutStrategy checkOutStrategy) {
    super(client);
    this.rootPath = rootPath;
    this.checkOutStrategy = checkOutStrategy;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      rootPath, router);

    routeRegistration.create(this::checkOut);
  }

  private void checkOut(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    CheckOutByBarcodeRequest request = CheckOutByBarcodeRequest.fromJson(
      routingContext.getBodyAsJson());

    final Clients clients = Clients.create(context, client);

    final UserRepository userRepository = new UserRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanService loanService = new LoanService(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final OverdueFinePolicyRepository overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
    final LostItemPolicyRepository lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    final PatronNoticePolicyRepository patronNoticePolicyRepository = new PatronNoticePolicyRepository(clients);
    final PatronGroupRepository patronGroupRepository = new PatronGroupRepository(clients);
    final ConfigurationRepository configurationRepository = new ConfigurationRepository(clients);
    final ScheduledNoticesRepository scheduledNoticesRepository = ScheduledNoticesRepository.using(clients);
    final LoanScheduledNoticeService scheduledNoticeService =
      new LoanScheduledNoticeService(scheduledNoticesRepository, patronNoticePolicyRepository);

    CirculationErrorHandler errorHandler = new DeferFailureErrorHandler();
    CheckOutValidators validators = new CheckOutValidators(request, clients, errorHandler);

    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final PatronActionSessionService patronActionSessionService =
      PatronActionSessionService.using(clients);

    ofAsync(() -> new LoanAndRelatedRecords(request.toLoan()))
      .thenApply(validators::refuseCheckOutWhenServicePointIsNotPresent)
      .thenComposeAsync(r -> getUserByBarcode(request.getUserBarcode(), userRepository, errorHandler)
        .thenApply(userResult -> addUser(r, userResult, errorHandler)))
      .thenApply(validators::refuseWhenUserIsInactive)
      .thenComposeAsync(validators::refuseWhenCheckOutActionIsBlockedForPatron)
      .thenComposeAsync(r -> getProxyUserByBarcode(request.getProxyUserBarcode(), userRepository, errorHandler)
        .thenApply(userResult -> addProxyUser(r, userResult, errorHandler)))
      .thenApply(validators::refuseWhenProxyUserIsInactive)
      .thenComposeAsync(validators::refuseWhenProxyUserIsTheSameAsUser)
      .thenComposeAsync(r -> getItemByBarcode(request.getItemBarcode(), itemRepository, errorHandler)
        .thenApply(itemResult -> addItem(r, itemResult, errorHandler)))
      .thenApply(validators::refuseWhenItemNotFound)
      .thenApply(validators::refuseWhenItemIsAlreadyCheckedOut)
      .thenApply(validators::refuseWhenItemIsNotAllowedForCheckOut)
      .thenComposeAsync(validators::refuseWhenItemHasOpenLoans)
      .thenComposeAsync(r -> r.after(l -> getRequestQueue(l, requestQueueRepository, errorHandler)))
      .thenApply(validators::refuseWhenRequestedByAnotherPatron)
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenComposeAsync(validators::refuseWhenItemLimitIsReached)
      .thenApply(r -> validators.refuseWhenItemIsNotLoanable(r, checkOutStrategy))
      .thenApply(errorHandler::failWithValidationErrors)
      .thenCompose(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        LoanAndRelatedRecords::withTimeZone))
      .thenComposeAsync(r -> r.after(overdueFinePolicyRepository::lookupOverdueFinePolicy))
      .thenComposeAsync(r -> r.after(lostItemPolicyRepository::lookupLostItemPolicy))
      .thenApply(r -> r.next(this::setItemLocationIdAtCheckout))
      .thenComposeAsync(r -> r.after(relatedRecords -> checkOutStrategy.checkOut(relatedRecords,
        routingContext.getBodyAsJson(), clients)))
      .thenApply(r -> r.map(this::checkOutItem))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(loanService::truncateLoanWhenItemRecalled))
      .thenComposeAsync(r -> r.after(patronGroupRepository::findPatronGroupForLoanAndRelatedRecords))
      .thenComposeAsync(r -> r.after(l -> updateItem(l, itemRepository)))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenComposeAsync(r -> r.after(patronActionSessionService::saveCheckOutSessionRecord))
      .thenComposeAsync(r -> r.after(eventPublisher::publishItemCheckedOutEvent))
      .thenApply(r -> r.next(scheduledNoticeService::scheduleNoticesForLoanDueDate))
      .thenApply(r -> r.map(LoanAndRelatedRecords::getLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(this::createdLoanFrom)
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> updateItem(
    LoanAndRelatedRecords loanAndRelatedRecords, ItemRepository itemRepository) {

    return itemRepository.updateItem(loanAndRelatedRecords.getItem())
      .thenApply(r -> r.map(loanAndRelatedRecords::withItem));
  }

  private LoanAndRelatedRecords checkOutItem(LoanAndRelatedRecords loanAndRelatedRecords) {
    return loanAndRelatedRecords.changeItemStatus(CHECKED_OUT);
  }

  private Result<HttpResponse> createdLoanFrom(Result<JsonObject> result) {
    return result.map(json -> created(json, urlForLoan(json.getString("id"))));
  }

  private String urlForLoan(String id) {
    return String.format("/circulation/loans/%s", id);
  }

  private CompletableFuture<Result<User>> getUserByBarcode(String barcode,
    UserRepository userRepository, CirculationErrorHandler errorHandler) {

    return userRepository.getUserByBarcode(barcode)
      .thenApply(r -> errorHandler.handleValidationResult(r, FAILED_TO_FETCH_USER, succeeded(null)));
  }

  private Result<LoanAndRelatedRecords> addUser(Result<LoanAndRelatedRecords> loanResult,
    Result<User> getUserResult, CirculationErrorHandler errorHandler) {

    if (getUserResult.value() == null || errorHandler.hasAny(FAILED_TO_FETCH_USER)) {
      return loanResult;
    }

    return loanResult.combine(getUserResult, LoanAndRelatedRecords::withRequestingUser)
      .mapFailure(failure -> errorHandler.handleValidationError(failure, FAILED_TO_FETCH_USER, loanResult));
  }

  private CompletableFuture<Result<User>> getProxyUserByBarcode(String barcode,
    UserRepository userRepository, CirculationErrorHandler errorHandler) {

    return userRepository.getProxyUserByBarcode(barcode)
      .thenApply(r -> errorHandler.handleValidationResult(r, FAILED_TO_FETCH_PROXY_USER, succeeded(null)));
  }

  private Result<LoanAndRelatedRecords> addProxyUser(Result<LoanAndRelatedRecords> loanResult,
    Result<User> getUserResult, CirculationErrorHandler errorHandler) {

    if (getUserResult.value() == null ||
      errorHandler.hasAny(FAILED_TO_FETCH_PROXY_USER)) {

      return loanResult;
    }

    return loanResult.combine(getUserResult, LoanAndRelatedRecords::withProxyingUser)
      .mapFailure(failure -> errorHandler.handleValidationError(failure, FAILED_TO_FETCH_PROXY_USER, loanResult));
  }

  private CompletableFuture<Result<Item>> getItemByBarcode(String barcode,
    ItemRepository itemRepository, CirculationErrorHandler errorHandler) {

    return itemRepository.fetchByBarcode(barcode)
      .thenApply(r -> errorHandler.handleValidationResult(r, FAILED_TO_FETCH_ITEM, succeeded(null)));
  }

  private Result<LoanAndRelatedRecords> addItem(Result<LoanAndRelatedRecords> loanResult,
    Result<Item> inventoryRecordsResult, CirculationErrorHandler errorHandler) {

    if (inventoryRecordsResult.value() == null ||
      errorHandler.hasAny(FAILED_TO_FETCH_ITEM)) {

      return loanResult;
    }

    return loanResult.combine(inventoryRecordsResult, LoanAndRelatedRecords::withItem);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> getRequestQueue(
    LoanAndRelatedRecords loanAndRelatedRecords, RequestQueueRepository requestQueueRepository,
    CirculationErrorHandler errorHandler) {

    return requestQueueRepository.get(loanAndRelatedRecords)
      .thenApply(r -> errorHandler.handleValidationResult(r, FAILED_TO_FETCH_REQUEST_QUEUE, loanAndRelatedRecords));
  }

  private Result<LoanAndRelatedRecords> setItemLocationIdAtCheckout(
    LoanAndRelatedRecords relatedRecords) {

    return succeeded(relatedRecords.withItemEffectiveLocationIdAtCheckOut());
  }
}
