package org.folio.circulation.domain;

import static java.util.Optional.ofNullable;
import static org.folio.circulation.domain.representations.logs.RequestUpdateLogEventMapper.mapToRequestCreatedLogEventJson;
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

  public CreateRequestService(CreateRequestRepositories repositories,
                              UpdateUponRequest updateUponRequest, RequestLoanValidator requestLoanValidator,
                              RequestNoticeSender requestNoticeSender, UserManualBlocksValidator userManualBlocksValidator, EventPublisher eventPublisher) {
    this.repositories = repositories;
    this.updateUponRequest = updateUponRequest;
    this.requestLoanValidator = requestLoanValidator;
    this.requestNoticeSender = requestNoticeSender;
    this.userManualBlocksValidator = userManualBlocksValidator;
    this.eventPublisher = eventPublisher;
  }

  public CreateRequestService(CreateRequestRepositories repositories,
    UpdateUponRequest updateUponRequest, RequestLoanValidator requestLoanValidator,
    RequestNoticeSender requestNoticeSender, UserManualBlocksValidator userManualBlocksValidator) {
    this(repositories, updateUponRequest, requestLoanValidator, requestNoticeSender, userManualBlocksValidator, null);
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> createRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {

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

    return of(() -> requestAndRelatedRecords)
      .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
      .next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
      .next(RequestServiceUtility::refuseWhenItemIsNotValid)
      .next(RequestServiceUtility::refuseWhenUserIsInactive)
      .next(RequestServiceUtility::refuseWhenUserHasAlreadyRequestedItem)
      .after(requestLoanValidator::refuseWhenUserHasAlreadyBeenLoanedItem)
      .thenComposeAsync(r -> r.after(userManualBlocksValidator::refuseWhenUserIsBlocked))
      .thenComposeAsync(r -> r.after(
        automatedPatronBlocksValidator::refuseWhenRequestActionIsBlockedForPatron))
      .thenComposeAsync(r -> r.after(requestPolicyRepository::lookupRequestPolicy))
      .thenComposeAsync(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        RequestAndRelatedRecords::withTimeZone))
      .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateItem::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateLoan::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(requestRepository::create))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateRequestQueue::onCreate))
      .thenApplyAsync(r -> {
        ofNullable(eventPublisher).ifPresent(publisher ->
          requestRepository.getById(requestAndRelatedRecords.getRequest().getId())
          .thenAcceptAsync(resp -> resp.after(t -> publisher.publishLogRecordEvent(mapToRequestCreatedLogEventJson(t)))));
        return r.next(requestNoticeSender::sendNoticeOnRequestCreated);
      });
  }

}
