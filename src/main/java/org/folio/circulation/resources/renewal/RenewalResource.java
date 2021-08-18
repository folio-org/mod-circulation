package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.OverdueFineCalculatorService.using;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.domain.override.OverridableBlockType.PATRON_BLOCK;
import static org.folio.circulation.domain.override.OverridableBlockType.RENEWAL_BLOCK;
import static org.folio.circulation.resources.RenewalValidator.CAN_NOT_RENEW_ITEM_ERROR;
import static org.folio.circulation.resources.RenewalValidator.FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD;
import static org.folio.circulation.resources.RenewalValidator.FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD_FOR_HOLDS;
import static org.folio.circulation.resources.RenewalValidator.errorForDueDate;
import static org.folio.circulation.resources.RenewalValidator.errorForNotMatchingOverrideCases;
import static org.folio.circulation.resources.RenewalValidator.errorForRecallRequest;
import static org.folio.circulation.resources.RenewalValidator.errorWhenEarlierOrSameDueDate;
import static org.folio.circulation.resources.RenewalValidator.itemByIdValidationError;
import static org.folio.circulation.resources.RenewalValidator.loanPolicyValidationError;
import static org.folio.circulation.resources.RenewalValidator.overrideDueDateIsRequiredError;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FIND_SINGLE_OPEN_LOAN;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INSUFFICIENT_OVERRIDE_PERMISSIONS;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.RENEWAL_DUE_DATE_REQUIRED_IS_BLOCKED;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.RENEWAL_IS_BLOCKED;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.RENEWAL_IS_NOT_POSSIBLE;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.RENEWAL_VALIDATION_ERROR;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_INACTIVE;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.OverdueFineCalculatorService;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeService;
import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.domain.validation.AutomatedPatronBlocksValidator;
import org.folio.circulation.domain.validation.InactiveUserRenewalValidator;
import org.folio.circulation.domain.validation.UserManualBlocksValidator;
import org.folio.circulation.domain.validation.Validator;
import org.folio.circulation.domain.validation.overriding.BlockValidator;
import org.folio.circulation.domain.validation.overriding.OverridingBlockValidator;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.LoanNoticeSender;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.CirculationErrorType;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.LostItemFeeRefundService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public abstract class RenewalResource extends Resource {
  private final String rootPath;
  private static final String COMMENT = "comment";
  private static final String DUE_DATE = "dueDate";
  private static final String OVERRIDE_BLOCKS = "overrideBlocks";
  private static final String RENEWAL_DUE_DATE_REQUIRED_OVERRIDE_BLOCK = "renewalDueDateRequiredBlock";
  private static final EnumSet<ItemStatus> ITEM_STATUSES_DISALLOWED_FOR_RENEW = EnumSet.of(
    AGED_TO_LOST, DECLARED_LOST);
  private static final EnumSet<ItemStatus> ITEM_STATUSES_NOT_POSSIBLE_TO_RENEW = EnumSet.of(
    CLAIMED_RETURNED);
  private boolean isRenewalBlockOverrideRequested;

  RenewalResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      rootPath, router);

    routeRegistration.create(this::renew);
  }

  private void renew(RoutingContext routingContext) {
    final WebContext webContext = new WebContext(routingContext);
    final Clients clients = Clients.create(webContext, client);
    final OkapiPermissions okapiPermissions = OkapiPermissions.from(webContext.getHeaders());

    final CirculationErrorHandler errorHandler = new OverridingErrorHandler(okapiPermissions);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final UserRepository userRepository = new UserRepository(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final StoreLoanAndItem storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final ConfigurationRepository configurationRepository = new ConfigurationRepository(clients);
    final LoanScheduledNoticeService scheduledNoticeService = LoanScheduledNoticeService.using(clients);

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients);

    final AutomatedPatronBlocksRepository automatedPatronBlocksRepository =
      new AutomatedPatronBlocksRepository(clients);
    final FeeFineScheduledNoticeService feeFineNoticesService =
      FeeFineScheduledNoticeService.using(clients);

    //TODO: Validation check for same user should be in the domain service
    JsonObject bodyAsJson = routingContext.getBodyAsJson();
    BlockOverrides overrideBlocks = getOverrideBlocks(bodyAsJson);
    OkapiPermissions permissions = OkapiPermissions.from(new WebContext(routingContext).getHeaders());
    final Validator<RenewalContext> automatedPatronBlocksValidator =
      createAutomatedPatronBlocksValidator(bodyAsJson, permissions, automatedPatronBlocksRepository);
    final Validator<RenewalContext> manualPatronBlocksValidator = createManualPatronBlocksValidator(
      bodyAsJson, permissions, clients);
    final Validator<RenewalContext> overrideRenewValidator = new OverridingBlockValidator<>(
      RENEWAL_BLOCK, overrideBlocks, permissions);
    isRenewalBlockOverrideRequested = overrideBlocks.getRenewalBlockOverride().isRequested() ||
      overrideBlocks.getRenewalDueDateRequiredBlockOverride().isRequested();

    findLoan(bodyAsJson, loanRepository, itemRepository, userRepository, errorHandler)
      .thenApply(r -> r.map(loan -> RenewalContext.create(loan, bodyAsJson, webContext.getUserId())))
      .thenComposeAsync(r-> refuseWhenPatronIsInactive(r, errorHandler, USER_IS_INACTIVE))
      .thenComposeAsync(r -> refuseWhenRenewalActionIsBlockedForPatron(
        manualPatronBlocksValidator, r, errorHandler, USER_IS_BLOCKED_MANUALLY))
      .thenComposeAsync(r -> refuseWhenRenewalActionIsBlockedForPatron(
        automatedPatronBlocksValidator, r, errorHandler, USER_IS_BLOCKED_AUTOMATICALLY))
      .thenComposeAsync(r -> refuseIfNoPermissionsForRenewalOverride(
        overrideRenewValidator, r, errorHandler))
      .thenCompose(r -> r.after(ctx -> lookupLoanPolicy(ctx, loanPolicyRepository, errorHandler)))
      .thenComposeAsync(r -> r.after(
        ctx -> lookupRequestQueue(ctx, requestQueueRepository, errorHandler)))
      .thenCompose(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        RenewalContext::withTimeZone))
      .thenComposeAsync(r -> r.after(context -> renew(context, clients, errorHandler)))
      .thenApply(r -> r.next(errorHandler::failWithValidationErrors))
      .thenComposeAsync(r -> r.after(storeLoanAndItem::updateLoanAndItemInStorage))
      .thenComposeAsync(r -> r.after(context -> processFeesFines(context, clients)))
      .thenApplyAsync(r -> r.next(feeFineNoticesService::scheduleOverdueFineNotices))
      .thenComposeAsync(r -> r.after(eventPublisher::publishDueDateChangedEvent))
      .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
      .thenApply(r -> r.next(loanNoticeSender::sendRenewalPatronNotice))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(r -> r.map(this::toResponse))
      .thenAccept(webContext::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<RenewalContext>> processFeesFines(RenewalContext renewalContext,
    Clients clients) {

    return isRenewalBlockOverrideRequested
      ? processFeesFinesForRenewalBlockOverride(renewalContext, clients)
      : processFeesFinesForRegularRenew(renewalContext, clients);
  }

  private CompletableFuture<Result<RenewalContext>> processFeesFinesForRenewalBlockOverride(
    RenewalContext renewalContext, Clients clients) {

    final LostItemFeeRefundService lostFeeRefundService = new LostItemFeeRefundService(clients);

    return lostFeeRefundService.refundLostItemFees(renewalContext, servicePointId(renewalContext))
      .thenCompose(r -> r.after(context -> processFeesFinesForRegularRenew(context, clients)));
  }

  private CompletableFuture<Result<RenewalContext>> processFeesFinesForRegularRenew(
    RenewalContext renewalContext, Clients clients) {

    final OverdueFineCalculatorService overdueFineService = using(clients);
    return overdueFineService.createOverdueFineIfNecessary(renewalContext);
  }

  private String servicePointId(RenewalContext renewalContext) {
    return renewalContext.getRenewalRequest().getString("servicePointId");
  }

  private CompletableFuture<Result<RenewalContext>> refuseWhenPatronIsInactive(
    Result<RenewalContext> result, CirculationErrorHandler errorHandler,
    CirculationErrorType errorType) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN,
      FAILED_TO_FETCH_USER)) {

      return completedFuture(result);
    }

    final var inactiveUserRenewalValidator = new InactiveUserRenewalValidator();

    final var validator = new BlockValidator<>(USER_IS_INACTIVE,
      inactiveUserRenewalValidator::refuseWhenPatronIsInactive);

    return result.after(renewalContext -> validator.validate(renewalContext)
      .thenApply(r -> errorHandler.handleValidationResult(r, errorType, result)));
  }

  private CompletableFuture<Result<RenewalContext>> refuseWhenRenewalActionIsBlockedForPatron(
    Validator<RenewalContext> validator, Result<RenewalContext> result,
    CirculationErrorHandler errorHandler, CirculationErrorType errorType) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN,
      FAILED_TO_FETCH_USER)) {

      return completedFuture(result);
    }

    return result.after(renewalContext -> validator.validate(renewalContext)
      .thenApply(r -> errorHandler.handleValidationResult(r, errorType, result)));
  }

  private CompletableFuture<Result<RenewalContext>> lookupLoanPolicy(
    RenewalContext renewalContext, LoanPolicyRepository loanPolicyRepository,
    CirculationErrorHandler errorHandler) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN,
      FAILED_TO_FETCH_USER)) {

      return completedFuture(succeeded(renewalContext));
    }

    return loanPolicyRepository.lookupLoanPolicy(renewalContext);
  }

  private CompletableFuture<Result<RenewalContext>> lookupRequestQueue(
    RenewalContext renewalContext, RequestQueueRepository requestQueueRepository,
    CirculationErrorHandler errorHandler) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN)) {
      return completedFuture(succeeded(renewalContext));
    }

    return requestQueueRepository.get(renewalContext);
  }

  private CompletableFuture<Result<RenewalContext>> renew(
    RenewalContext renewalContext, Clients clients, CirculationErrorHandler errorHandler) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN,
      FAILED_TO_FETCH_USER)) {

      return completedFuture(succeeded(renewalContext));
    }

    if (isRenewalBlockOverrideRequested) {
      return renewThroughOverride(renewalContext)
        .thenApply(r -> errorHandler.handleValidationResult(r, RENEWAL_VALIDATION_ERROR,
          renewalContext));
    }
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    final ClosedLibraryStrategyService strategyService = ClosedLibraryStrategyService.using(
      clients, systemTime, true);

    return regularRenew(renewalContext, errorHandler, systemTime)
      .after(strategyService::applyClosedLibraryDueDateManagement);
  }

  private HttpResponse toResponse(JsonObject body) {
    return JsonHttpResponse.ok(body,
      String.format("/circulation/loans/%s", body.getString("id")));
  }

  protected abstract CompletableFuture<Result<Loan>> findLoan(JsonObject request,
    LoanRepository loanRepository, ItemRepository itemRepository,
    UserRepository userRepository, CirculationErrorHandler errorHandler);

  private Validator<RenewalContext> createAutomatedPatronBlocksValidator(JsonObject request,
    OkapiPermissions permissions, AutomatedPatronBlocksRepository automatedPatronBlocksRepository) {

    Function<RenewalContext, CompletableFuture<Result<RenewalContext>>> validationFunction =
      new AutomatedPatronBlocksValidator(
        automatedPatronBlocksRepository)::refuseWhenRenewalActionIsBlockedForPatron;

    final BlockOverrides blockOverrides = getOverrideBlocks(request);

    return blockOverrides.getPatronBlockOverride().isRequested()
      ? new OverridingBlockValidator<>(PATRON_BLOCK, blockOverrides, permissions)
      : new BlockValidator<>(USER_IS_BLOCKED_AUTOMATICALLY, validationFunction);
  }

  private Validator<RenewalContext> createInactivePatronValidator() {
    final var inactiveUserRenewalValidator = new InactiveUserRenewalValidator();

    return new BlockValidator<>(USER_IS_INACTIVE, inactiveUserRenewalValidator::refuseWhenPatronIsInactive);
}

  private Validator<RenewalContext> createManualPatronBlocksValidator(JsonObject request,
    OkapiPermissions permissions, Clients clients) {

    Function<RenewalContext, CompletableFuture<Result<RenewalContext>>> validationFunction =
      new UserManualBlocksValidator(clients)::refuseWhenUserIsBlocked;

    final BlockOverrides blockOverrides = getOverrideBlocks(request);

    return blockOverrides.getPatronBlockOverride().isRequested()
      ? new OverridingBlockValidator<>(PATRON_BLOCK, blockOverrides, permissions)
      : new BlockValidator<>(USER_IS_BLOCKED_MANUALLY, validationFunction);
  }

  private BlockOverrides getOverrideBlocks(JsonObject request) {
    return BlockOverrides.from(getObjectProperty(request, "overrideBlocks"));
  }

  private CompletableFuture<Result<RenewalContext>> refuseIfNoPermissionsForRenewalOverride(
    Validator<RenewalContext> validator, Result<RenewalContext> result,
    CirculationErrorHandler errorHandler) {

    return isRenewalBlockOverrideRequested
      ? result.after(validator::validate)
        .thenApply(r -> errorHandler.handleValidationResult(r, INSUFFICIENT_OVERRIDE_PERMISSIONS,
          result))
      : completedFuture(result);
  }

  public CompletableFuture<Result<RenewalContext>> renewThroughOverride(RenewalContext context) {
    final JsonObject overrideBlocks = context.getRenewalRequest().getJsonObject(OVERRIDE_BLOCKS);
    final String comment = getProperty(overrideBlocks, COMMENT);
    if (StringUtils.isBlank(comment)) {
      return completedFuture(failedValidation("Override renewal request must have a comment",
        COMMENT, null));
    }
    final DateTime overrideDueDate = getDateTimeProperty(overrideBlocks.getJsonObject(
      RENEWAL_DUE_DATE_REQUIRED_OVERRIDE_BLOCK), DUE_DATE);

    Loan loan = context.getLoan();
    boolean hasRecallRequest =
      context.getRequestQueue().getRequests().stream().findFirst()
        .map(r -> r.getRequestType() == RequestType.RECALL)
        .orElse(false);

    return completedFuture(overrideRenewal(loan, DateTime.now(DateTimeZone.UTC),
      overrideDueDate, comment, hasRecallRequest))
      .thenApply(mapResult(context::withLoan));
  }

  private Result<Loan> overrideRenewal(Loan loan, DateTime systemDate,
    DateTime overrideDueDate, String comment, boolean hasRecallRequest) {

    try {
      final LoanPolicy loanPolicy = loan.getLoanPolicy();

      if (loanPolicy.isNotLoanable() || loanPolicy.isNotRenewable()) {
        return overrideRenewalForDueDate(loan, overrideDueDate, comment);
      }

      if (unableToCalculateProposedDueDate(loan, systemDate)) {
        return overrideRenewalForDueDate(loan, overrideDueDate, comment);
      }

      final Result<DateTime> newDueDateResult = calculateNewDueDate(overrideDueDate, loan, systemDate);

      if (loanPolicy.hasReachedRenewalLimit(loan)) {
        return processRenewal(newDueDateResult, loan, comment);
      }

      if (hasRecallRequest) {
        return processRenewal(newDueDateResult, loan, comment);
      }

      if (loan.isItemLost()) {
        return processRenewal(newDueDateResult, loan, comment);
      }

      if (proposedDueDateIsSameOrEarlier(loan, systemDate)) {
        return processRenewal(newDueDateResult, loan, comment);
      }

      return failedValidation(errorForNotMatchingOverrideCases(loanPolicy));

    } catch (Exception e) {
      return failedDueToServerError(e);
    }
  }

  private Result<Loan> overrideRenewalForDueDate(Loan loan, DateTime overrideDueDate, String comment) {
    if (overrideDueDate == null) {
      return failedValidation(errorForDueDate());
    }
    return succeeded(overrideRenewLoan(overrideDueDate, loan, comment));
  }

  private Result<Loan> processRenewal(Result<DateTime> calculatedDueDate, Loan loan, String comment) {
    return calculatedDueDate
      .next(dueDate -> errorWhenEarlierOrSameDueDate(loan, dueDate))
      .map(dueDate -> overrideRenewLoan(dueDate, loan, comment));
  }

  private Loan overrideRenewLoan(DateTime dueDate, Loan loan, String comment) {
    if (loan.isAgedToLost()) {
      loan.removeAgedToLostBillingInfo();
    }

    return loan.overrideRenewal(dueDate, loan.getLoanPolicyId(), comment)
      .changeItemStatusForItemAndLoan(CHECKED_OUT);
  }

  private Result<DateTime> calculateNewDueDate(DateTime overrideDueDate, Loan loan, DateTime systemDate) {
    final Result<DateTime> proposedDateTimeResult = calculateProposedDueDate(loan, systemDate);

    if (newDueDateAfterCurrentDueDate(loan, proposedDateTimeResult)) {
      return proposedDateTimeResult;
    }

    if (overrideDueDate == null && proposedDueDateIsSameOrEarlier(loan, systemDate)) {
      return failedValidation(overrideDueDateIsRequiredError());
    }

    return succeeded(overrideDueDate);
  }

  private Result<DateTime> calculateProposedDueDate(Loan loan, DateTime systemDate) {
    return loan.getLoanPolicy()
      .determineStrategy(null, true, false, systemDate).calculateDueDate(loan);
  }

  private boolean newDueDateAfterCurrentDueDate(Loan loan, Result<DateTime> proposedDueDateResult) {
    return proposedDueDateResult.map(proposedDueDate -> proposedDueDate.isAfter(loan.getDueDate()))
      .orElse(false);
  }

  private boolean unableToCalculateProposedDueDate(Loan loan, DateTime systemDate) {
    return calculateProposedDueDate(loan, systemDate).failed();
  }

  private boolean proposedDueDateIsSameOrEarlier(Loan loan, DateTime systemDate) {
    return !newDueDateAfterCurrentDueDate(loan, calculateProposedDueDate(loan, systemDate));
  }

  public Result<RenewalContext> regularRenew(RenewalContext context,
    CirculationErrorHandler errorHandler, DateTime renewDate) {

    return validateIfRenewIsAllowed(context, false)
      .mapFailure(failure -> errorHandler.handleValidationError(failure,
        RENEWAL_IS_BLOCKED, context))
      .next(ctx -> validateIfRenewIsAllowed(context, true)
        .mapFailure(failure -> errorHandler.handleValidationError(failure,
          RENEWAL_DUE_DATE_REQUIRED_IS_BLOCKED, context)))
      .next(this::validateIfRenewIsPossible)
        .mapFailure(failure -> errorHandler.handleValidationError(failure,
          RENEWAL_IS_NOT_POSSIBLE, context))
      .next(ctx -> renew(ctx, renewDate)
        .mapFailure(failure -> errorHandler.handleValidationError(failure,
          RENEWAL_DUE_DATE_REQUIRED_IS_BLOCKED, context)));
  }

  private Result<RenewalContext> validateIfRenewIsAllowed(RenewalContext context,
    boolean isDueDateRequired) {

    Loan loan = context.getLoan();
    RequestQueue requestQueue = context.getRequestQueue();
    try {
      final var errors = isDueDateRequired
        ? validateIfRenewIsAllowedAndDueDateRequired(loan, requestQueue)
        : validateIfRenewIsAllowedAndDueDateNotRequired(loan, requestQueue);
      final var loanPolicy = loan.getLoanPolicy();
      if (loanPolicy.isNotLoanable() || loanPolicy.isNotRenewable()) {
        return failedValidation(errors);
      }
      if (errors.isEmpty()) {
        return succeeded(context);
      }

      return failedValidation(errors);

    } catch (Exception e) {
      return failedDueToServerError(e);
    }
  }

  private Result<RenewalContext> validateIfRenewIsPossible(RenewalContext context) {
    Loan loan = context.getLoan();
    if (ITEM_STATUSES_NOT_POSSIBLE_TO_RENEW.contains(loan.getItemStatus())) {
      final List<ValidationError> errors = new ArrayList<>();
      errors.add(itemByIdValidationError("item is " + loan.getItemStatusName(), loan.getItemId()));
      return failedValidation(errors);
    }
    return succeeded(context);
  }

  private Result<RenewalContext> renew(RenewalContext context, DateTime renewDate) {
    final var loan = context.getLoan();
    final var requestQueue = context.getRequestQueue();
    final var loanPolicy = loan.getLoanPolicy();

    final Result<DateTime> proposedDueDateResult = calculateNewDueDate(loan, requestQueue,
      renewDate);
    final List<ValidationError> errors = new ArrayList<>();
    addErrorsIfDueDateResultFailed(loan, errors, proposedDueDateResult);

    if (errors.isEmpty()) {
      final BlockOverrides blockOverrides = BlockOverrides.from(getObjectProperty(
        context.getRenewalRequest(), "overrideBlocks"));

      if (!blockOverrides.getPatronBlockOverride().isRequested() &&
        !blockOverrides.getRenewalBlockOverride().isRequested()) {

        return proposedDueDateResult
          .map(dueDate -> loan.renew(dueDate, loanPolicy.getId()))
          .map(l -> context);
      }
      return proposedDueDateResult
        .map(dueDate -> loan.overrideRenewal(
          dueDate, loanPolicy.getId(), blockOverrides.getComment()))
        .map(l -> context);
    }
    return failedValidation(errors);
  }

  private void addErrorsIfDueDateResultFailed(Loan loan, List<ValidationError> errors,
    Result<DateTime> proposedDueDateResult) {

    if (proposedDueDateResult.failed()) {
      if (proposedDueDateResult.cause() instanceof ValidationErrorFailure) {
        var failureCause = (ValidationErrorFailure) proposedDueDateResult.cause();

        errors.addAll(failureCause.getErrors());
      }
    } else {
      errorWhenEarlierOrSameDueDate(loan, proposedDueDateResult.value(), errors);
    }
  }

  private Result<DateTime> calculateNewDueDate(Loan loan, RequestQueue requestQueue,
    DateTime systemDate) {

    final var loanPolicy = loan.getLoanPolicy();
    final var isRenewalWithHoldRequest = isHold(getFirstRequestInQueue(requestQueue));

    return loanPolicy.determineStrategy(null, true, isRenewalWithHoldRequest, systemDate)
      .calculateDueDate(loan);
  }

  private List<ValidationError> validateIfRenewIsAllowedAndDueDateNotRequired(Loan loan,
    RequestQueue requestQueue) {

    final List<ValidationError> errors = new ArrayList<>();
    final LoanPolicy loanPolicy = loan.getLoanPolicy();
    final Request firstRequest = getFirstRequestInQueue(requestQueue);

    if (hasRecallRequest(firstRequest)) {
      errors.add(errorForRecallRequest(
        "items cannot be renewed when there is an active recall request",
        firstRequest.getId()));
    }
    if (ITEM_STATUSES_DISALLOWED_FOR_RENEW.contains(loan.getItemStatus())) {
      errors.add(itemByIdValidationError("item is " + loan.getItemStatusName(),
        loan.getItemId()));
    }

    if (loanPolicy.hasReachedRenewalLimit(loan)) {
      errors.add(loanPolicyValidationError(loanPolicy, "loan at maximum renewal number"));
    }

    return errors;
  }

  private List<ValidationError> validateIfRenewIsAllowedAndDueDateRequired(Loan loan,
    RequestQueue requestQueue) {

    final List<ValidationError> errors = new ArrayList<>();
    final LoanPolicy loanPolicy = loan.getLoanPolicy();
    final Request firstRequest = getFirstRequestInQueue(requestQueue);

    if (loanPolicy.isNotLoanable()) {
      errors.add(loanPolicyValidationError(loanPolicy, "item is not loanable"));
    } else if (loanPolicy.isNotRenewable()) {
      errors.add(loanPolicyValidationError(loanPolicy, "loan is not renewable"));
    }
    if (isHold(firstRequest)) {
      if (!loanPolicy.isHoldRequestRenewable()) {
        errors.add(loanPolicyValidationError(loanPolicy, CAN_NOT_RENEW_ITEM_ERROR));
      }

      if (loanPolicy.isFixed()) {
        if (loanPolicy.hasAlternateRenewalLoanPeriodForHolds()) {
          errors.add(loanPolicyValidationError(loanPolicy,
            FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD_FOR_HOLDS));
        }
        if (loanPolicy.hasRenewalPeriod()) {
          errors.add(loanPolicyValidationError(loanPolicy,
            FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD));
        }
      }
    }

    return errors;
  }

  private Request getFirstRequestInQueue(RequestQueue requestQueue) {
    return requestQueue.getRequests().stream()
      .findFirst().orElse(null);
  }

  private boolean hasRecallRequest(Request firstRequest) {
    return firstRequest != null && firstRequest.getRequestType() == RECALL;
  }

  private boolean isHold(Request request) {
    return request != null && request.getRequestType() == HOLD;
  }
}
