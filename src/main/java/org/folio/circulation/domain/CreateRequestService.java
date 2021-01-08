package org.folio.circulation.domain;

import static org.folio.circulation.domain.RequestServiceUtility.refuseWhenRequestCannotBeFulfilled;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_CREATED;
import static org.folio.circulation.domain.representations.logs.RequestUpdateLogEventMapper.mapToRequestLogEventJson;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_REQUEST_POLICY;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_TIME_ZONE_CONFIG;
import static org.folio.circulation.resources.error.CirculationError.INVALID_ITEM;
import static org.folio.circulation.resources.error.CirculationError.INVALID_USER_OR_PATRON_GROUP;
import static org.folio.circulation.resources.error.CirculationError.ITEM_ALREADY_LOANED_TO_SAME_USER;
import static org.folio.circulation.resources.error.CirculationError.ITEM_ALREADY_REQUESTED_BY_SAME_USER;
import static org.folio.circulation.resources.error.CirculationError.REQUESTING_DISALLOWED_BY_REQUEST_POLICY;
import static org.folio.circulation.resources.error.CirculationError.REQUESTING_DISALLOWED_FOR_ITEM;
import static org.folio.circulation.resources.error.CirculationError.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.resources.error.CirculationError.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.resources.error.CirculationError.USER_IS_INACTIVE;
import static org.folio.circulation.support.results.Result.of;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.domain.validation.AutomatedPatronBlocksValidator;
import org.folio.circulation.domain.validation.RequestLoanValidator;
import org.folio.circulation.domain.validation.UserManualBlocksValidator;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.resources.error.CirculationErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;

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
      RequestAndRelatedRecords request) {

    RequestRepository requestRepository = repositories.getRequestRepository();
    RequestPolicyRepository requestPolicyRepository = repositories.getRequestPolicyRepository();
    ConfigurationRepository configurationRepository = repositories.getConfigurationRepository();
    AutomatedPatronBlocksRepository automatedPatronBlocksRepository =
      repositories.getAutomatedPatronBlocksRepository();
    final AutomatedPatronBlocksValidator automatedPatronBlocksValidator =
      new AutomatedPatronBlocksValidator(automatedPatronBlocksRepository,
        messages -> new ValidationErrorFailure(messages.stream()
          .map(message -> new ValidationError(message, new HashMap<>()))
          .collect(Collectors.toList())));

    return of(() -> request)
      .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
      .mapFailure(error -> errorHandler.handle(error, INVALID_ITEM, request))
      // TODO: split into 2 checks?
      .next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
      .mapFailure(error -> errorHandler.handle(error, INVALID_USER_OR_PATRON_GROUP, request))
      .next(RequestServiceUtility::refuseWhenItemIsNotValid)
      .mapFailure(error -> errorHandler.handle(error, REQUESTING_DISALLOWED_FOR_ITEM, request))
      .next(RequestServiceUtility::refuseWhenUserIsInactive)
      .mapFailure(error -> errorHandler.handle(error, USER_IS_INACTIVE, request))
      .next(RequestServiceUtility::refuseWhenUserHasAlreadyRequestedItem)
      .mapFailure(error -> errorHandler.handle(error, ITEM_ALREADY_REQUESTED_BY_SAME_USER, request))
      .after(req -> requestLoanValidator.refuseWhenUserHasAlreadyBeenLoanedItem(req)
        .thenApply(r -> errorHandler.handle(r, ITEM_ALREADY_LOANED_TO_SAME_USER, req)))
      .thenComposeAsync(r -> r.after(req -> userManualBlocksValidator.refuseWhenUserIsBlocked(req)
        .thenApply(res -> errorHandler.handle(res, USER_IS_BLOCKED_MANUALLY, req))))
      .thenComposeAsync(r -> r.after(req -> automatedPatronBlocksValidator.refuseWhenRequestActionIsBlockedForPatron(req)
        .thenApply(res -> errorHandler.handle(res, USER_IS_BLOCKED_AUTOMATICALLY, req))))
      .thenComposeAsync(r -> r.after(req -> requestPolicyRepository.lookupRequestPolicy(req)
        .thenApply(res -> errorHandler.handle(res, FAILED_TO_FETCH_REQUEST_POLICY, req))))
      .thenComposeAsync(r -> r.after(req -> configurationRepository.findTimeZoneConfiguration()
        .thenApply(res -> res.map(req::withTimeZone))
        .thenApply(res -> errorHandler.handle(res, FAILED_TO_FETCH_TIME_ZONE_CONFIG, req))))
      .thenApply(r -> r.next(req -> refuseWhenRequestCannotBeFulfilled(req)
        .mapFailure(error -> errorHandler.handle(error, REQUESTING_DISALLOWED_BY_REQUEST_POLICY, req))))

      .thenApply(r -> r.next(errorHandler::failIfErrorsExist))

      .thenComposeAsync(r -> r.after(updateUponRequest.updateItem::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateLoan::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(requestRepository::create))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateRequestQueue::onCreate))
      .thenApplyAsync(r -> {
        r.after(t -> eventPublisher.publishLogRecord(mapToRequestLogEventJson(t.getRequest()), REQUEST_CREATED));
        return r.next(requestNoticeSender::sendNoticeOnRequestCreated);
      });
  }

}
