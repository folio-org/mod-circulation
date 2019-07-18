package org.folio.circulation.domain;

import static org.folio.circulation.support.Result.of;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.domain.validation.RequestLoanValidator;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.support.Result;

public class CreateRequestService {
  private final RequestRepository requestRepository;
  private final RequestPolicyRepository requestPolicyRepository;
  private final UpdateUponRequest updateUponRequest;
  private final RequestLoanValidator requestLoanValidator;
  private final RequestNoticeSender requestNoticeSender;

  public CreateRequestService(RequestRepository requestRepository, RequestPolicyRepository requestPolicyRepository,
      UpdateUponRequest updateUponRequest, RequestLoanValidator requestLoanValidator,
      RequestNoticeSender requestNoticeSender) {
    this.requestRepository = requestRepository;
    this.requestPolicyRepository = requestPolicyRepository;
    this.updateUponRequest = updateUponRequest;
    this.requestLoanValidator = requestLoanValidator;
    this.requestNoticeSender = requestNoticeSender;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> createRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    return of(() -> requestAndRelatedRecords)
      .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
      .next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
      .next(RequestServiceUtility::refuseWhenItemIsNotValid)
      .next(RequestServiceUtility::refuseWhenUserHasAlreadyRequestedItem)
      .after(requestLoanValidator::refuseWhenUserHasAlreadyBeenLoanedItem)
      .thenComposeAsync(r -> r.after(requestPolicyRepository::lookupRequestPolicy))
      .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateItem::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateLoanActionHistory::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateLoan::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(requestRepository::create))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateRequestQueue::onCreate))
      .thenApply(r -> r.next(requestNoticeSender::sendNoticeOnRequestCreated));
  }
}
