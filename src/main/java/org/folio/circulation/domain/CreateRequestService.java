package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_CREATED;
import static org.folio.circulation.domain.representations.logs.RequestUpdateLogEventMapper.mapToRequestLogEventJson;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.AUTOMATED_BLOCKS_VALIDATION_FAILED;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_REQUEST_POLICY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_TIME_ZONE_CONFIG;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_ITEM_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_PATRON_GROUP_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_ALREADY_LOANED_TO_SAME_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_ALREADY_REQUESTED_BY_SAME_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_VALIDATION_FAILED;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.MANUAL_BLOCKS_VALIDATION_FAILED;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.REQUESTING_DISALLOWED_BY_POLICY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.REQUESTING_DISALLOWED;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_INACTIVE;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_VALIDATION_FAILED;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.validation.AutomatedPatronBlocksValidator;
import org.folio.circulation.domain.validation.RequestLoanValidator;
import org.folio.circulation.domain.validation.UserManualBlocksValidator;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public class CreateRequestService {
  private final CreateRequestRepositories repositories;
  private final UpdateUponRequest updateUponRequest;
  private final RequestLoanValidator requestLoanValidator;
  private final RequestNoticeSender requestNoticeSender;
  private final UserManualBlocksValidator userManualBlocksValidator;
  private final EventPublisher eventPublisher;
  private final CirculationErrorHandler errorHandler;

  public CreateRequestService(CreateRequestRepositories repositories,
    UpdateUponRequest updateUponRequest, RequestLoanValidator requestLoanValidator,
    RequestNoticeSender requestNoticeSender, UserManualBlocksValidator userManualBlocksValidator,
    EventPublisher eventPublisher, CirculationErrorHandler errorHandler) {

    this.repositories = repositories;
    this.updateUponRequest = updateUponRequest;
    this.requestLoanValidator = requestLoanValidator;
    this.requestNoticeSender = requestNoticeSender;
    this.userManualBlocksValidator = userManualBlocksValidator;
    this.eventPublisher = eventPublisher;
    this.errorHandler = errorHandler;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> createRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    return ofAsync(() -> requestAndRelatedRecords)
      .thenApply(this::checkRequester)
      .thenComposeAsync(this::checkItem)
      .thenComposeAsync(this::checkBlocks)
      .thenComposeAsync(this::checkRequestPolicy)
      .thenComposeAsync(this::fetchTimeZoneConfiguration)
      .thenApply(r -> r.next(errorHandler::failIfHasErrors))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateItem::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateLoan::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(repositories.getRequestRepository()::create))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateRequestQueue::onCreate))
      .thenApplyAsync(r -> {
        r.after(t -> eventPublisher.publishLogRecord(mapToRequestLogEventJson(t.getRequest()), REQUEST_CREATED));
        return r.next(requestNoticeSender::sendNoticeOnRequestCreated);
      });
  }

  private Result<RequestAndRelatedRecords> checkRequester(Result<RequestAndRelatedRecords> result) {
    return result.next(RequestServiceUtility::refuseWhenInvalidUser)
      .mapFailure(err -> errorHandler.handleValidationError(err, USER_DOES_NOT_EXIST, result))
      .next(RequestServiceUtility::refuseWhenInvalidPatronGroupId)
      .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_PATRON_GROUP_ID, result))
      .next(RequestServiceUtility::refuseWhenUserIsInactive)
      .mapFailure(err -> errorHandler.handleValidationError(err, USER_IS_INACTIVE, result))
      .next(RequestServiceUtility::refuseWhenUserHasAlreadyRequestedItem)
      .mapFailure(err -> errorHandler.handleValidationError(err, ITEM_ALREADY_REQUESTED_BY_SAME_USER, result))
      .mapFailure(err -> errorHandler.handleError(err, USER_VALIDATION_FAILED, result));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> checkItem(
    Result<RequestAndRelatedRecords> result) {

    if (errorHandler.hasAny(INVALID_ITEM_ID)) {
      return completedFuture(result);
    }

    return result.next(RequestServiceUtility::refuseWhenItemDoesNotExist)
      .mapFailure(err -> errorHandler.handleValidationError(err, ITEM_DOES_NOT_EXIST, result))
      .next(RequestServiceUtility::refuseWhenRequestTypeIsNotAllowedForItem)
      .mapFailure(err -> errorHandler.handleValidationError(err, REQUESTING_DISALLOWED, result))
      .after(requestLoanValidator::refuseWhenUserHasAlreadyBeenLoanedItem)
      .thenApply(r -> errorHandler.handleValidationResult(r, ITEM_ALREADY_LOANED_TO_SAME_USER, result))
      .thenApply(r -> errorHandler.handleResult(r, ITEM_VALIDATION_FAILED, result));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> checkBlocks(
    Result<RequestAndRelatedRecords> result) {

    final AutomatedPatronBlocksValidator automatedPatronBlocksValidator =
      new AutomatedPatronBlocksValidator(repositories.getAutomatedPatronBlocksRepository(),
        messages -> new ValidationErrorFailure(messages.stream()
          .map(message -> new ValidationError(message, new HashMap<>()))
          .collect(Collectors.toList())));

    return result.after(automatedPatronBlocksValidator::refuseWhenRequestActionIsBlockedForPatron)
      .thenApply(r -> errorHandler.handleValidationResult(r, USER_IS_BLOCKED_AUTOMATICALLY, result))
      .thenApply(r -> errorHandler.handleResult(r, AUTOMATED_BLOCKS_VALIDATION_FAILED, result))
      .thenCompose(r -> r.after(userManualBlocksValidator::refuseWhenUserIsBlocked))
      .thenApply(r -> errorHandler.handleValidationResult(r, USER_IS_BLOCKED_MANUALLY, result))
      .thenApply(r -> errorHandler.handleResult(r, MANUAL_BLOCKS_VALIDATION_FAILED, result));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> checkRequestPolicy(
    Result<RequestAndRelatedRecords> result) {

    if (errorHandler.hasAny(INVALID_ITEM_ID, ITEM_DOES_NOT_EXIST,
      USER_DOES_NOT_EXIST, INVALID_PATRON_GROUP_ID)) {

      return completedFuture(result);
    }

    return result.after(repositories.getRequestPolicyRepository()::lookupRequestPolicy)
      .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled)
        .mapFailure(err -> errorHandler.handleValidationError(err, REQUESTING_DISALLOWED_BY_POLICY, r)))
      .thenApply(r -> errorHandler.handleResult(r, FAILED_TO_FETCH_REQUEST_POLICY, result));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> fetchTimeZoneConfiguration(
    Result<RequestAndRelatedRecords> result) {

    return result.combineAfter(repositories.getConfigurationRepository()::findTimeZoneConfiguration,
      RequestAndRelatedRecords::withTimeZone)
      .thenApply(r -> errorHandler.handleResult(r, FAILED_TO_FETCH_TIME_ZONE_CONFIG, result));
  }

}
