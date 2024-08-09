package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.LoanAction.CHECKED_OUT_THROUGH_OVERRIDE;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_ITEM;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_PROXY_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_PUBLISH_CHECKOUT_EVENT;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_SAVE_SESSION_RECORD;
import static org.folio.circulation.support.http.server.JsonHttpResponse.created;
import static org.folio.circulation.support.http.server.JsonHttpResponse.ok;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CheckOutLock;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.LoanService;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeService;
import org.folio.circulation.domain.notice.schedule.ReminderFeeScheduledNoticeService;
import org.folio.circulation.domain.notice.schedule.RequestScheduledNoticeService;
import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.domain.validation.CheckOutValidators;
import org.folio.circulation.infrastructure.storage.CheckOutLockRepository;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.sessions.PatronActionSessionRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.CirculationErrorType;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CheckOutByBarcodeResource extends Resource {

  private final String rootPath;
  final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final CirculationErrorType[] PARTIAL_SUCCESS_ERRORS = {
    FAILED_TO_SAVE_SESSION_RECORD, FAILED_TO_PUBLISH_CHECKOUT_EVENT};

  public CheckOutByBarcodeResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
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

    final var userRepository = new UserRepository(clients);
    final var itemRepository = new ItemRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var requestRepository = RequestRepository.using(clients, itemRepository,
      userRepository, loanRepository);
    final var requestQueueRepository = new RequestQueueRepository(requestRepository);
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
    final ReminderFeeScheduledNoticeService reminderFeeScheduledNoticesService =
      new ReminderFeeScheduledNoticeService(clients);

    OkapiPermissions permissions = OkapiPermissions.from(new WebContext(routingContext).getHeaders());
    CirculationErrorHandler errorHandler = new OverridingErrorHandler(permissions);
    CheckOutValidators validators = new CheckOutValidators(request, clients, errorHandler,
      permissions, loanRepository);

    final var requestQueueUpdate = UpdateRequestQueue.using(clients,
      requestRepository, requestQueueRepository);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final PatronActionSessionService patronActionSessionService =
      PatronActionSessionService.using(clients,
        PatronActionSessionRepository.using(clients, loanRepository,
          userRepository));

    final var requestScheduledNoticeService = RequestScheduledNoticeService.using(clients);

    final CheckOutLockRepository checkOutLockRepository = new CheckOutLockRepository(clients, routingContext);

    AtomicReference<String> checkOutLockId = new AtomicReference<>();

    final SettingsRepository settingsRepository = new SettingsRepository(clients);

    ofAsync(() -> new LoanAndRelatedRecords(request.toLoan()))
      .thenApply(validators::refuseCheckOutWhenServicePointIsNotPresent)
      .thenComposeAsync(r -> lookupUser(request.getUserBarcode(), userRepository, r, errorHandler))
      .thenComposeAsync(validators::refuseWhenCheckOutActionIsBlockedManuallyForPatron)
      .thenComposeAsync(validators::refuseWhenCheckOutActionIsBlockedAutomaticallyForPatron)
      .thenComposeAsync(r -> lookupProxyUser(request.getProxyUserBarcode(), userRepository, r, errorHandler))
      .thenApply(validators::refuseWhenUserIsInactive)
      .thenApply(validators::refuseWhenProxyUserIsInactive)
      .thenComposeAsync(validators::refuseWhenInvalidProxyRelationship)
      .thenComposeAsync(r -> lookupItem(request.getItemBarcode(), itemRepository, r))
      .thenApply(validators::refuseWhenItemNotFound)
      .thenApply(validators::refuseWhenItemIsAlreadyCheckedOut)
      .thenApply(validators::refuseWhenItemIsNotAllowedForCheckOut)
      .thenComposeAsync(validators::refuseWhenItemHasOpenLoans)
      .thenComposeAsync(r -> r.combineAfter(settingsRepository::lookupTlrSettings,
        LoanAndRelatedRecords::withTlrSettings))
      .thenComposeAsync(r -> r.combineAfter(l -> getRequestQueue(l, requestQueueRepository),
          LoanAndRelatedRecords::withRequestQueue))
      .thenCompose(validators::refuseWhenRequestedByAnotherPatron)
      .thenComposeAsync(r -> r.after(l -> lookupLoanPolicy(l, loanPolicyRepository, errorHandler)))
      .thenComposeAsync(validators::refuseWhenItemLimitIsReached)
      .thenCompose(validators::refuseWhenItemIsNotLoanable)
      .thenApply(r -> r.next(errorHandler::failWithValidationErrors))
      .thenCompose(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        LoanAndRelatedRecords::withTimeZone))
      .thenComposeAsync(r -> r.after(overdueFinePolicyRepository::lookupOverdueFinePolicy))
      .thenComposeAsync(r -> r.after(lostItemPolicyRepository::lookupLostItemPolicy))
      .thenApply(r -> r.next(this::setItemLocationIdAtCheckout))
      .thenComposeAsync(r -> r.after(relatedRecords -> checkOut(relatedRecords, clients)))
      .thenApply(r -> r.map(this::checkOutItem))
      .thenCompose(r -> r.after(l -> acquireLockIfNeededOrFail(settingsRepository,
        checkOutLockRepository, l, checkOutLockId, validators, errorHandler)))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(requestScheduledNoticeService::rescheduleRequestNotices))
      .thenComposeAsync(r -> r.after(loanService::truncateLoanWhenItemRecalled))
      .thenComposeAsync(r -> r.after(patronGroupRepository::findPatronGroupForLoanAndRelatedRecords))
      .thenComposeAsync(r -> r.after(l -> updateItem(l, itemRepository)))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenComposeAsync(r -> r.after(l -> saveCheckOutSessionRecord(l, patronActionSessionService,
        errorHandler)))
      .thenApply(r -> deleteCheckOutLock(r, checkOutLockRepository, checkOutLockId.get()))
      .thenApplyAsync(r -> r.map(records -> records.withLoggedInUserId(context.getUserId())))
      .thenComposeAsync(r -> r.after(l -> publishItemCheckedOutEvent(l, eventPublisher,
        userRepository, errorHandler)))
      .thenApply(r -> r.next(scheduledNoticeService::scheduleNoticesForLoanDueDate))
      .thenApply(r -> r.next(reminderFeeScheduledNoticesService::scheduleFirstReminder))
      .thenApply(r -> r.map(LoanAndRelatedRecords::getLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(r -> createdLoanFrom(r, errorHandler))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> acquireLockIfNeededOrFail(
    SettingsRepository settingsRepository, CheckOutLockRepository checkOutLockRepository,
    LoanAndRelatedRecords loanAndRelatedRecords, AtomicReference<String> checkOutLockId,
    CheckOutValidators validators, CirculationErrorHandler errorHandler) {

    log.debug("acquireLockIfNeededOrFail:: parameters loanAndRelatedRecords: {}",
      () -> loanAndRelatedRecords);

    return settingsRepository.lookUpCheckOutLockSettings()
      .thenApply(cr -> succeeded(loanAndRelatedRecords).combine(cr,
        LoanAndRelatedRecords::withCheckoutLockConfiguration))
      .thenCompose(r -> r.after(records -> this.acquireLock(records, checkOutLockRepository,
        checkOutLockId)))
      .thenCompose(r -> r.after(records -> this.validateItemLimitBasedOnLockFeatureFlag(records,
        validators, errorHandler)));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> saveCheckOutSessionRecord(
    LoanAndRelatedRecords records, PatronActionSessionService patronActionSessionService,
    CirculationErrorHandler errorHandler) {

    log.debug("saveCheckOutSessionRecord:: parameters records: {}", () -> records);

    return patronActionSessionService.saveCheckOutSessionRecord(records)
      .thenApply(r -> errorHandler.handleAnyResult(r, FAILED_TO_SAVE_SESSION_RECORD,
        succeeded(records)));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> acquireLock(LoanAndRelatedRecords records,
    CheckOutLockRepository checkOutLockRepository, AtomicReference<String> checkOutLockId) {

    log.debug("acquireLock:: Creating checkout lock {} ", records.getCheckoutLockConfiguration());
    if (records.isCheckoutLockFeatureEnabled()) {
      CompletableFuture<CheckOutLock> future = new CompletableFuture<>();
      checkOutLockRepository.createLockWithRetry(0, future, records);
      return future.handle((res, err) -> {
        if (err != null) {
          log.error("acquireLock:: Unable to acquire lock for item {} ", records.getItem().getBarcode(), err);
          return Result.failed(ValidationErrorFailure.singleValidationError("unable to acquire lock", "", ""));
        }
        checkOutLockId.set(res.getId());
        return Result.succeeded(records);
      });
    }
    return completedFuture(Result.succeeded(records));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> validateItemLimitBasedOnLockFeatureFlag(
    LoanAndRelatedRecords records, CheckOutValidators validators, CirculationErrorHandler errorHandler) {

    log.debug("validateItemLimitBasedOnLockFeatureFlag:: parameters records: {}", () -> records);

    if (records.isCheckoutLockFeatureEnabled()) {
      log.warn("validateItemLimitBasedOnLockFeatureFlag:: check out lock feature enabled");
      return validators.refuseWhenItemLimitIsReached(Result.of(() -> records))
        .thenApply(r -> r.next(errorHandler::failWithValidationErrors));
    }
    return completedFuture(Result.succeeded(records));
  }

  private Result<LoanAndRelatedRecords> deleteCheckOutLock(Result<LoanAndRelatedRecords> records,
    CheckOutLockRepository checkOutLockRepository, String checkOutLockId) {

    log.debug("deleteCheckOutLock:: parameters checkOutLockId: {}", checkOutLockId);

    if (StringUtils.isBlank(checkOutLockId)) {
      return records;
    }
    checkOutLockRepository.deleteCheckoutLockById(checkOutLockId);
    return records;
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> publishItemCheckedOutEvent(
    LoanAndRelatedRecords records, EventPublisher eventPublisher,
    UserRepository userRepository, CirculationErrorHandler errorHandler) {

    log.debug("publishItemCheckedOutEvent:: parameters records: {}", () -> records);

    return eventPublisher.publishItemCheckedOutEvent(records, userRepository)
      .thenApply(r -> errorHandler.handleAnyResult(r, FAILED_TO_PUBLISH_CHECKOUT_EVENT,
        succeeded(records)));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> lookupLoanPolicy(
    LoanAndRelatedRecords loanAndRelatedRecords, LoanPolicyRepository loanPolicyRepository,
    CirculationErrorHandler errorHandler) {

    log.debug("lookupLoanPolicy:: parameters loanAndRelatedRecords: {}", () -> loanAndRelatedRecords);

    if (errorHandler.hasAny(FAILED_TO_FETCH_ITEM)) {
      return completedFuture(succeeded(loanAndRelatedRecords));
    }

    return loanPolicyRepository.lookupLoanPolicy(loanAndRelatedRecords);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> updateItem(
    LoanAndRelatedRecords loanAndRelatedRecords, ItemRepository itemRepository) {

    log.debug("updateItem:: parameters loanAndRelatedRecords: {}", () -> loanAndRelatedRecords);

    return itemRepository.updateItem(loanAndRelatedRecords.getItem())
      .thenApply(r -> r.map(loanAndRelatedRecords::withItem));
  }

  private LoanAndRelatedRecords checkOutItem(LoanAndRelatedRecords loanAndRelatedRecords) {
    return loanAndRelatedRecords.changeItemStatus(CHECKED_OUT);
  }

  private Result<HttpResponse> createdLoanFrom(Result<JsonObject> result,
    CirculationErrorHandler errorHandler) {

    return result.map(json -> {
      String url = urlForLoan(json.getString("id"));

      return errorHandler.hasAny(PARTIAL_SUCCESS_ERRORS)
        ? ok(json, url)
        : created(json, url);
    });
  }

  private String urlForLoan(String id) {
    return String.format("/circulation/loans/%s", id);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> lookupUser(String barcode,
    UserRepository userRepository, Result<LoanAndRelatedRecords> loanResult,
    CirculationErrorHandler errorHandler) {

    log.debug("lookupUser:: parameters barcode: {}", barcode);

    return userRepository.getUserByBarcode(barcode)
      .thenApply(userResult -> loanResult.combine(userResult, LoanAndRelatedRecords::withRequestingUser))
      .thenApply(r -> errorHandler.handleValidationResult(r, FAILED_TO_FETCH_USER, loanResult));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> lookupProxyUser(String barcode,
    UserRepository userRepository, Result<LoanAndRelatedRecords> loanResult,
    CirculationErrorHandler errorHandler) {

    log.debug("lookupProxyUser:: parameters barcode: {}", barcode);

    return userRepository.getProxyUserByBarcode(barcode)
      .thenApply(userResult -> loanResult.combine(userResult, LoanAndRelatedRecords::withProxyingUser))
      .thenApply(r -> errorHandler.handleValidationResult(r, FAILED_TO_FETCH_PROXY_USER, loanResult));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> lookupItem(
    String barcode, ItemRepository itemRepository, Result<LoanAndRelatedRecords> loanResult) {

    log.debug("lookupItem:: parameters barcode: {}", barcode);

    return itemRepository.fetchByBarcode(barcode)
      .thenApply(itemResult -> loanResult.combine(itemResult, LoanAndRelatedRecords::withItem));
  }

  private Result<LoanAndRelatedRecords> setItemLocationIdAtCheckout(
    LoanAndRelatedRecords relatedRecords) {

    log.debug("setItemLocationIdAtCheckout:: parameters relatedRecords: {}", () -> relatedRecords);

    return succeeded(relatedRecords.withItemEffectiveLocationIdAtCheckOut());
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> checkOut(
    LoanAndRelatedRecords relatedRecords, Clients clients) {

    log.debug("checkOut:: parameters relatedRecords: {}", () -> relatedRecords);

    ZonedDateTime loanDate = relatedRecords.getLoan().getLoanDate();
    final ClosedLibraryStrategyService strategyService =
      ClosedLibraryStrategyService.using(clients, loanDate, false);

    if (CHECKED_OUT_THROUGH_OVERRIDE.getValue().equals(relatedRecords.getLoan().getAction())
      && relatedRecords.getLoan().hasDueDateChanged()) {

      log.info("checkOut:: check out through override");

      return completedFuture(succeeded(relatedRecords));
    }

    return completedFuture(succeeded(relatedRecords))
      .thenApply(r -> r.next(this::calculateDefaultInitialDueDate))
      .thenCompose(r -> r.after(strategyService::applyClosedLibraryDueDateManagement));
  }

  private Result<LoanAndRelatedRecords> calculateDefaultInitialDueDate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    log.debug("calculateDefaultInitialDueDate:: parameters loanAndRelatedRecords: {}",
      () -> loanAndRelatedRecords);
    Loan loan = loanAndRelatedRecords.getLoan();
    LoanPolicy loanPolicy = loan.getLoanPolicy();
    RequestQueue requestQueue = loanAndRelatedRecords.getRequestQueue();

    return loanPolicy.calculateInitialDueDate(loan, requestQueue)
      .map(loan::changeDueDate)
      .map(loanAndRelatedRecords::withLoan);
  }

  private CompletableFuture<Result<RequestQueue>> getRequestQueue(
    LoanAndRelatedRecords loanAndRelatedRecords, RequestQueueRepository requestQueueRepository) {

    Item item = loanAndRelatedRecords.getItem();

    return loanAndRelatedRecords.getTlrSettings().isTitleLevelRequestsFeatureEnabled()
      ? requestQueueRepository.getByInstanceIdAndItemId(item.getInstanceId(), item.getItemId())
      : requestQueueRepository.getByItemId(item.getItemId());
  }
}
