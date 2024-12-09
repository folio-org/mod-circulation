package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.EcsRequestPhase.INTERMEDIATE;
import static org.folio.circulation.domain.EcsRequestPhase.PRIMARY;
import static org.folio.circulation.domain.RequestLevel.TITLE;
import static org.folio.circulation.domain.representations.RequestProperties.INSTANCE_ID;
import static org.folio.circulation.domain.representations.RequestProperties.ITEM_ID;
import static org.folio.circulation.domain.representations.RequestProperties.REQUESTER_ID;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_CREATED;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_CREATED_THROUGH_OVERRIDE;
import static org.folio.circulation.domain.representations.logs.RequestUpdateLogEventMapper.mapToRequestLogEventJson;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ATTEMPT_HOLD_OR_RECALL_TLR_FOR_AVAILABLE_ITEM;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ATTEMPT_TO_CREATE_TLR_LINKED_TO_AN_ITEM;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INSTANCE_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_INSTANCE_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_ITEM_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_USER_OR_PATRON_GROUP_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_ALREADY_LOANED_TO_SAME_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_ALREADY_REQUESTED_BY_SAME_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ONE_OF_INSTANCES_ITEMS_HAS_OPEN_LOAN;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.REQUESTING_DISALLOWED;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.REQUESTING_DISALLOWED_BY_POLICY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.REQUEST_NOT_ALLOWED_FOR_PATRON_TITLE_COMBINATION;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.TLR_RECALL_WITHOUT_OPEN_LOAN_OR_RECALLABLE_ITEM;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_INACTIVE;
import static org.folio.circulation.support.ErrorCode.HOLD_AND_RECALL_TLR_NOT_ALLOWED_PAGEABLE_AVAILABLE_ITEM_FOUND;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.logResult;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.representations.logs.LogEventType;
import org.folio.circulation.domain.validation.RequestLoanValidator;
import org.folio.circulation.resources.RequestBlockValidators;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.ItemForTlrService;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.request.RequestRelatedRepositories;
import org.folio.circulation.support.results.Result;

public class CreateRequestService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final RequestRelatedRepositories repositories;
  private final UpdateUponRequest updateUponRequest;
  private final RequestLoanValidator requestLoanValidator;
  private final RequestNoticeSender requestNoticeSender;
  private final RequestBlockValidators requestBlockValidators;
  private final EventPublisher eventPublisher;
  private final CirculationErrorHandler errorHandler;

  public CreateRequestService(RequestRelatedRepositories repositories,
    UpdateUponRequest updateUponRequest, RequestLoanValidator requestLoanValidator,
    RequestNoticeSender requestNoticeSender, RequestBlockValidators requestBlockValidators,
    EventPublisher eventPublisher, CirculationErrorHandler errorHandler) {

    this.repositories = repositories;
    this.updateUponRequest = updateUponRequest;
    this.requestLoanValidator = requestLoanValidator;
    this.requestNoticeSender = requestNoticeSender;
    this.requestBlockValidators = requestBlockValidators;
    this.eventPublisher = eventPublisher;
    this.errorHandler = errorHandler;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> createRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("createRequest:: parameters requestAndRelatedRecords: {}", () -> requestAndRelatedRecords);

    final var requestRepository = repositories.getRequestRepository();
    final var configurationRepository = repositories.getConfigurationRepository();
    final var automatedBlocksValidator = requestBlockValidators.getAutomatedPatronBlocksValidator();
    final var manualBlocksValidator = requestBlockValidators.getManualPatronBlocksValidator();

    final Result<RequestAndRelatedRecords> result = succeeded(requestAndRelatedRecords);

    return result.next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
      .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_USER_OR_PATRON_GROUP_ID, result))
      .next(RequestServiceUtility::refuseWhenUserIsInactive)
      .mapFailure(err -> errorHandler.handleValidationError(err, USER_IS_INACTIVE, result))
      .next(RequestServiceUtility::refuseWhenAlreadyRequested)
      .mapFailure(err -> errorHandler.handleValidationError(err, ITEM_ALREADY_REQUESTED_BY_SAME_USER, result))
      .after(automatedBlocksValidator::validate)
      .thenApply(r -> errorHandler.handleValidationResult(r, automatedBlocksValidator.getErrorType(), result))
      .thenCompose(r -> r.after(manualBlocksValidator::validate))
      .thenApply(r -> errorHandler.handleValidationResult(r, manualBlocksValidator.getErrorType(), result))
      .thenComposeAsync(r -> r.after(when(this::shouldCheckInstance, this::checkInstance, this::doNothing)))
      .thenComposeAsync(r -> r.after(when(this::shouldCheckItem, this::checkItem, this::doNothing)))
      .thenComposeAsync(r -> r.after(this::checkPolicy))
      .thenApply(r -> r.next(this::refuseHoldOrRecallTlrWhenPageableItemExists))
      .thenComposeAsync(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        RequestAndRelatedRecords::withTimeZone))
      .thenApply(r -> r.next(errorHandler::failWithValidationErrors))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateItem::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateLoan::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(requestRepository::create))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateRequestQueue::onCreate))
      .thenApplyAsync(r -> {
        r.after(t -> eventPublisher.publishLogRecord(mapToRequestLogEventJson(t.getRequest()), getLogEventType()));
        return r.next(requestNoticeSender::sendNoticeOnRequestCreated);
      }).thenApply(r -> logResult(r, "createRequest"));
  }

  private Result<RequestAndRelatedRecords> refuseHoldOrRecallTlrWhenPageableItemExists(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();
    if (request.isTitleLevel() && (request.isHold() || request.isRecall())) {
      log.info("refuseHoldOrRecallTlrWhenPageableItemExists:: request is title-level Hold or Recall");
      List<Item> availablePageableItems = ItemForTlrService.using(repositories)
        .findAvailablePageableItems(requestAndRelatedRecords.getRequest());

      return failValidationWhenPageableItemsExist(requestAndRelatedRecords, availablePageableItems)
        .mapFailure(err -> errorHandler.handleValidationError(err,
          ATTEMPT_HOLD_OR_RECALL_TLR_FOR_AVAILABLE_ITEM, requestAndRelatedRecords));
    }

    return of(() -> requestAndRelatedRecords);
  }

  private Result<RequestAndRelatedRecords> failValidationWhenPageableItemsExist(
    RequestAndRelatedRecords requestAndRelatedRecords, List<Item> availablePageableItems) {

    if (availablePageableItems.isEmpty()) {
      log.info("failValidationWhenPageableItemsExist:: no available pageable items found");
      return succeeded(requestAndRelatedRecords);
    }

    String availablePageableItemId = availablePageableItems.stream()
      .map(Item::getItemId)
      .findAny()
      .orElse("");

    return failedValidationHoldAndRecallNotAllowed(requestAndRelatedRecords.getRequest(),
      availablePageableItemId);
  }

  private Result<RequestAndRelatedRecords> failedValidationHoldAndRecallNotAllowed(Request request,
    String availableItemId) {

    String errorMessage = "Hold/Recall title level request not allowed: pageable available item " +
      "found for instance";
    log.warn("failedValidationHoldAndRecallNotAllowed:: {}. Pageable available item: {}",
      errorMessage, availableItemId);

    return failedValidation(errorMessage, Map.of(ITEM_ID, availableItemId, INSTANCE_ID,
      request.getInstanceId()), HOLD_AND_RECALL_TLR_NOT_ALLOWED_PAGEABLE_AVAILABLE_ITEM_FOUND);
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> checkInstance(
    RequestAndRelatedRecords records) {

    return completedFuture(succeeded(records)
      .next(RequestServiceUtility::refuseWhenInstanceDoesNotExist)
      .mapFailure(err ->
        errorHandler.handleValidationError(err, INSTANCE_DOES_NOT_EXIST, records)));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> checkItem(
    RequestAndRelatedRecords records) {

    Request request = records.getRequest();
    RequestLevel requestLevel = request.getRequestLevel();
    log.info("checkItem:: request level: {}", requestLevel);
    log.debug("checkItem:: accumulated errors: {}", errorHandler::getErrors);

    if (records.isTlrFeatureEnabled() && requestLevel == TITLE) {
      log.info("checkItem:: checking title-level request");
      if (errorHandler.hasAny(ATTEMPT_TO_CREATE_TLR_LINKED_TO_AN_ITEM, ATTEMPT_HOLD_OR_RECALL_TLR_FOR_AVAILABLE_ITEM)) {
        log.warn("checkItem:: error(s) incompatible with TLR check detected, check aborted");
        return ofAsync(() -> records);
      }

      Result<RequestAndRelatedRecords> result = succeeded(records);
      String itemId = request.getItemId();
      if (itemId != null) {
        log.info("checkItem:: request contains itemId: {}", itemId);
        result = result
          .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
          .mapFailure(err -> errorHandler.handleValidationError(err, ITEM_DOES_NOT_EXIST, records));
      }

      if (errorHandler.hasNone(INVALID_INSTANCE_ID, INSTANCE_DOES_NOT_EXIST)) {
        log.info("checkItem:: checking if user already has an item of requested instance on loan");
        return result
          .after(requestLoanValidator::refuseWhenUserHasAlreadyBeenLoanedOneOfInstancesItems)
          .thenApply(r -> errorHandler.handleValidationResult(r, ONE_OF_INSTANCES_ITEMS_HAS_OPEN_LOAN, records));
      }
      return completedFuture(result);
    }

    return succeeded(records)
      .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
      .mapFailure(err -> errorHandler.handleValidationError(err, ITEM_DOES_NOT_EXIST, records))
      .next(RequestServiceUtility::refuseWhenRequestTypeIsNotAllowedForItem)
      .mapFailure(err -> errorHandler.handleValidationError(err, REQUESTING_DISALLOWED, records))
      .after(requestLoanValidator::refuseWhenUserHasAlreadyBeenLoanedItem)
      .thenApply(r -> errorHandler.handleValidationResult(r, ITEM_ALREADY_LOANED_TO_SAME_USER, records));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> checkPolicy(
    RequestAndRelatedRecords records) {

    log.debug("checkPolicy:: accumulated errors: {}", errorHandler::getErrors);
    if (errorHandler.hasAny(INVALID_INSTANCE_ID, INSTANCE_DOES_NOT_EXIST, INVALID_ITEM_ID,
      ITEM_DOES_NOT_EXIST, INVALID_USER_OR_PATRON_GROUP_ID,
      TLR_RECALL_WITHOUT_OPEN_LOAN_OR_RECALLABLE_ITEM)) {

      log.warn("checkPolicy:: error(s) incompatible with check detected, check aborted");
      return ofAsync(() -> records);
    }

    final Request request = records.getRequest();
    boolean tlrFeatureEnabled = request.getTlrSettingsConfiguration().isTitleLevelRequestsFeatureEnabled();

    if (tlrFeatureEnabled && request.isTitleLevel() && request.isHold()) {
      EcsRequestPhase ecsRequestPhase = request.getEcsRequestPhase();
      if (ecsRequestPhase == PRIMARY || ecsRequestPhase == INTERMEDIATE) {
        log.warn("checkPolicy:: ECS TLR Hold with phase {} detected, skipping policy check", ecsRequestPhase);
        return ofAsync(() -> records);
      }

      log.info("checkPolicy:: checking policy for title-level hold");
      return completedFuture(checkPolicyForTitleLevelHold(records));
    }

    return repositories.getRequestPolicyRepository().lookupRequestPolicy(records)
      .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled)
        .mapFailure(err -> errorHandler.handleValidationError(err, REQUESTING_DISALLOWED_BY_POLICY, r)));
  }

  private Result<RequestAndRelatedRecords> checkPolicyForTitleLevelHold(RequestAndRelatedRecords records) {
    final Request request = records.getRequest();

    if (request.getTlrSettingsConfiguration().isTlrHoldShouldFollowCirculationRules() &&
      noneOfInstanceItemsAreAllowedForHold(request)) {

      log.warn("checkPolicyForTlrHold:: none of the items of requested instance are allowed for " +
        "Hold requests according to circulation rules: requesterId={}, instanceId={}",
        request.getRequesterId(), request.getInstanceId());

      return ValidationErrorFailure.<RequestAndRelatedRecords>failedValidation(
          "Hold requests are not allowed for this patron and title combination",
          Map.of(REQUESTER_ID, request.getRequesterId(), INSTANCE_ID, request.getInstanceId()),
          ErrorCode.REQUEST_NOT_ALLOWED_FOR_PATRON_TITLE_COMBINATION)
        .mapFailure(err -> errorHandler.handleValidationError(err,
          REQUEST_NOT_ALLOWED_FOR_PATRON_TITLE_COMBINATION, records));
    }

    return succeeded(records);
  }

  private static boolean noneOfInstanceItemsAreAllowedForHold(Request request) {
    return request.getInstanceItemsRequestPolicies()
      .values()
      .stream()
      .noneMatch(policy -> policy.allowsType(RequestType.HOLD));
  }

  private CompletableFuture<Result<Boolean>> shouldCheckInstance(RequestAndRelatedRecords records) {
    log.debug("shouldCheckInstance:: accumulated errors: {}", errorHandler::getErrors);
    return ofAsync(() -> errorHandler.hasNone(INVALID_INSTANCE_ID));
  }

  private CompletableFuture<Result<Boolean>> shouldCheckItem(RequestAndRelatedRecords records) {
    log.debug("shouldCheckItem:: accumulated errors: {}", errorHandler::getErrors);
    return ofAsync(() -> errorHandler.hasNone(INVALID_INSTANCE_ID, INSTANCE_DOES_NOT_EXIST,
      INVALID_ITEM_ID));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> doNothing(
    RequestAndRelatedRecords records) {

    return ofAsync(() -> records);
  }

  private LogEventType getLogEventType() {
    return requestBlockValidators.isOverrideRequested()
      ? REQUEST_CREATED_THROUGH_OVERRIDE
      : REQUEST_CREATED;
  }

}
