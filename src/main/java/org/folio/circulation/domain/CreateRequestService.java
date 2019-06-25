package org.folio.circulation.domain;

import static org.folio.circulation.support.Result.of;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.support.Result;

public class CreateRequestService {
  private final RequestLoanService requestLoanService;
  private final RequestNoticeSender requestNoticeSender;

  public CreateRequestService(RequestLoanService requestLoanService, RequestNoticeSender requestNoticeSender) {
    this.requestLoanService = requestLoanService;
    this.requestNoticeSender = requestNoticeSender;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> createRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return of(() -> requestAndRelatedRecords)
      .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
      .next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
      .next(RequestServiceUtility::refuseWhenItemIsNotValid)
      .next(RequestServiceUtility::refuseWhenUserHasAlreadyRequestedItem)
      .after(requestLoanService::refuseWhenUserHasAlreadyBeenLoanedItem)
      .thenComposeAsync(r -> r.after(requestLoanService.requestPolicyRepository::lookupRequestPolicy))
      .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled))
      .thenApply(r -> r.map(RequestServiceUtility::setRequestQueuePosition))
      .thenComposeAsync(r -> r.after(requestLoanService.updateItem::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(requestLoanService.updateLoanActionHistory::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(requestLoanService.updateLoan::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(requestLoanService.requestRepository::create))
      .thenApply(r -> r.next(requestNoticeSender::sendNoticeOnRequestCreated));
  }
}
