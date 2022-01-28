package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_MOVED;
import static org.folio.circulation.support.results.Result.of;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.validation.RequestLoanValidator;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class MoveRequestService {
  private final RequestRepository requestRepository;
  private final RequestPolicyRepository requestPolicyRepository;
  private final UpdateUponRequest updateUponRequest;
  private final MoveRequestProcessAdapter moveRequestProcessAdapter;
  private final RequestLoanValidator requestLoanValidator;
  private final RequestNoticeSender requestNoticeSender;
  private final ConfigurationRepository configurationRepository;
  private final EventPublisher eventPublisher;

  public MoveRequestService(RequestRepository requestRepository,
                            RequestPolicyRepository requestPolicyRepository,
                            UpdateUponRequest updateUponRequest,
                            MoveRequestProcessAdapter moveRequestHelper,
                            RequestLoanValidator requestLoanValidator,
                            RequestNoticeSender requestNoticeSender,
                            ConfigurationRepository configurationRepository,
                            EventPublisher eventPublisher) {

    this.requestRepository = requestRepository;
    this.requestPolicyRepository = requestPolicyRepository;
    this.updateUponRequest = updateUponRequest;
    this.moveRequestProcessAdapter = moveRequestHelper;
    this.requestLoanValidator = requestLoanValidator;
    this.requestNoticeSender = requestNoticeSender;
    this.configurationRepository = configurationRepository;
    this.eventPublisher = eventPublisher;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> moveRequest(
      RequestAndRelatedRecords requestAndRelatedRecords, Request originalRequest) {
    return completedFuture(of(() -> requestAndRelatedRecords))
      .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::findDestinationItem))
      .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::getDestinationRequestQueue))
      .thenApply(r -> r.map(this::pagedRequestIfDestinationItemAvailable))
      .thenCompose(r -> r.after(this::validateUpdateRequest))
      .thenComposeAsync(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        RequestAndRelatedRecords::withTimeZone))
      .thenCompose(r -> r.after(updateUponRequest.updateRequestQueue::onMovedTo))
      .thenComposeAsync(r -> r.after(this::updateRelatedObjects))
      .thenCompose(r -> r.after(requestRepository::update))
      .thenApply(r -> r.next(requestNoticeSender::sendNoticeOnRequestMoved))
      .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::findSourceItem))
      .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::getSourceRequestQueue))
      .thenCompose(r -> r.after(updateUponRequest.updateRequestQueue::onMovedFrom))
      .thenComposeAsync(r -> r.after(this::updateRelatedObjects))
      .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::findDestinationItem))
      .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::getDestinationRequestQueue))
      .thenComposeAsync(r -> r.after(moveRequestProcessAdapter::getRequest))
      .thenApplyAsync(r -> r.map(u -> eventPublisher.publishLogRecordAsync(u, originalRequest, REQUEST_MOVED)));
  }

  private RequestAndRelatedRecords pagedRequestIfDestinationItemAvailable(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    Item item = requestAndRelatedRecords.getRequest().getItem();

    if (item.getStatus().equals(ItemStatus.AVAILABLE)) {
      return requestAndRelatedRecords.withRequestType(RequestType.PAGE);
    }

    return requestAndRelatedRecords;
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> validateUpdateRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    return of(() -> requestAndRelatedRecords)
      .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
      .next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
      .next(RequestServiceUtility::refuseWhenRequestTypeIsNotAllowedForItem)
      .next(RequestServiceUtility::refuseWhenAlreadyRequested)
      .after(requestLoanValidator::refuseWhenUserHasAlreadyBeenLoanedItem)
      .thenComposeAsync(r -> r.after(requestPolicyRepository::lookupRequestPolicy))
      .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> updateRelatedObjects(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    return updateUponRequest.updateItem.onRequestCreateOrUpdate(requestAndRelatedRecords)
      .thenComposeAsync(r -> r.after(updateUponRequest.updateLoan::onRequestCreateOrUpdate));
  }
}
