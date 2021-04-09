package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.override.OverridableBlockType.PATRON_BLOCK;
import static org.folio.circulation.domain.override.OverridableBlockType.RENEWAL_BLOCK;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FIND_SINGLE_OPEN_LOAN;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INSUFFICIENT_OVERRIDE_PERMISSIONS;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.RENEWAL_VALIDATION_ERROR;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeService;
import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.validation.AutomatedPatronBlocksValidator;
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
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public abstract class RenewalResource extends Resource {
  private final String rootPath;
  private RenewalStrategy renewalStrategy;
  private RenewalFeeProcessingStrategy feeProcessing;

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
    OkapiPermissions permissions = OkapiPermissions.from(new WebContext(routingContext).getHeaders());
    final Validator<RenewalContext> automatedPatronBlocksValidator =
      createAutomatedPatronBlocksValidator(bodyAsJson, permissions, automatedPatronBlocksRepository);
    final Validator<RenewalContext> manualPatronBlocksValidator = createManualPatronBlocksValidator(
      bodyAsJson, permissions, clients);
    final Validator<RenewalContext> overrideRenewValidator = new OverridingBlockValidator<>(
      RENEWAL_BLOCK, getOverrideBlocks(bodyAsJson), permissions);
    determineStrategy(bodyAsJson);

    findLoan(bodyAsJson, loanRepository, itemRepository, userRepository, errorHandler)
      .thenApply(r -> r.map(loan -> RenewalContext.create(loan, bodyAsJson, webContext.getUserId())))
      .thenComposeAsync(r -> refuseWhenRenewalActionIsBlockedForPatron(
        manualPatronBlocksValidator, r, errorHandler, USER_IS_BLOCKED_MANUALLY))
      .thenComposeAsync(r -> refuseWhenRenewalActionIsBlockedForPatron(
        automatedPatronBlocksValidator, r, errorHandler, USER_IS_BLOCKED_AUTOMATICALLY))
      .thenComposeAsync(r -> refuseIfNoPermissionsForRenewalOverriding(
        overrideRenewValidator, r, errorHandler))
      .thenCompose(r -> r.after(ctx -> lookupLoanPolicy(ctx, loanPolicyRepository, errorHandler)))
      .thenComposeAsync(r -> r.after(
        ctx -> lookupRequestQueue(ctx, requestQueueRepository, errorHandler)))
      .thenCompose(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        RenewalContext::withTimeZone))
      .thenComposeAsync(r -> r.after(context -> renew(context, clients, errorHandler)))
      .thenApply(r -> r.next(errorHandler::failWithValidationErrors))
      .thenComposeAsync(r -> r.after(storeLoanAndItem::updateLoanAndItemInStorage))
      .thenComposeAsync(r -> r.after(context -> feeProcessing.processFeesFines(context, clients)))
      .thenApplyAsync(r -> r.next(feeFineNoticesService::scheduleOverdueFineNotices))
      .thenComposeAsync(r -> r.after(eventPublisher::publishDueDateChangedEvent))
      .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
      .thenApply(r -> r.next(loanNoticeSender::sendRenewalPatronNotice))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(r -> r.map(this::toResponse))
      .thenAccept(webContext::writeResultToHttpResponse);
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

    return renewalStrategy.renew(renewalContext, clients)
      .thenApply(r -> errorHandler.handleValidationResult(r, RENEWAL_VALIDATION_ERROR, renewalContext));
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

  private void determineStrategy(JsonObject bodyAsJson) {
    if (getOverrideBlocks(bodyAsJson).getRenewalBlockOverride().isRequested()) {
      renewalStrategy = new OverrideRenewalStrategy();
      feeProcessing = new OverrideRenewalFeeProcessingStrategy();
    } else {
      renewalStrategy = new RegularRenewalStrategy();
      feeProcessing = new RegularRenewalFeeProcessingStrategy();
    }
  }

  private CompletableFuture<Result<RenewalContext>> refuseIfNoPermissionsForRenewalOverriding(
    Validator<RenewalContext> validator, Result<RenewalContext> result,
    CirculationErrorHandler errorHandler) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN)
      || renewalStrategy instanceof RegularRenewalStrategy) {

      return completedFuture(result);
    }

    return result.after(validator::validate)
      .thenApply(r -> errorHandler.handleValidationResult(r, INSUFFICIENT_OVERRIDE_PERMISSIONS,
        result));
  }
}
