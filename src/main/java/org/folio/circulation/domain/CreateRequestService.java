package org.folio.circulation.domain;

import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;

public class CreateRequestService {

  private final RequestRepository requestRepository;
  private final UpdateItem updateItem;
  private final UpdateLoanActionHistory updateLoanActionHistory;
  private final RequestPolicyRepository requestPolicyRepository;
  private final LoanRepository loanRepository;
  private final UpdateLoan updateLoan;
  private final RequestNoticeSender requestNoticeSender;

  public CreateRequestService(RequestRepository requestRepository, UpdateItem updateItem,
                              UpdateLoanActionHistory updateLoanActionHistory, UpdateLoan updateLoan,
                              RequestPolicyRepository requestPolicyRepository,
                              LoanRepository loanRepository, RequestNoticeSender requestNoticeSender) {

    this.requestRepository = requestRepository;
    this.updateItem = updateItem;
    this.updateLoanActionHistory = updateLoanActionHistory;
    this.updateLoan = updateLoan;
    this.requestPolicyRepository = requestPolicyRepository;
    this.loanRepository = loanRepository;
    this.requestNoticeSender = requestNoticeSender;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> createRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return of(() -> requestAndRelatedRecords)
      .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
      .next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
      .next(RequestServiceUtility::refuseWhenItemIsNotValid)
      .next(RequestServiceUtility::refuseWhenUserHasAlreadyRequestedItem)
      .after(this::refuseWhenUserHasAlreadyBeenLoanedItem)
      .thenComposeAsync(r -> r.after(requestPolicyRepository::lookupRequestPolicy))
      .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled))
      .thenApply(r -> r.map(RequestServiceUtility::setRequestQueuePosition))
      .thenComposeAsync(r -> r.after(updateItem::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(updateLoan::onRequestCreationOrMove))
      .thenComposeAsync(r -> r.after(requestRepository::create))
      .thenApply(r -> r.next(requestNoticeSender::sendNoticeOnRequestCreated));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenUserHasAlreadyBeenLoanedItem(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    return loanRepository.findOpenLoanForRequest(request)
      .thenApply(loanResult -> loanResult.failWhen(
        loan -> of(() -> loan != null && loan.getUserId().equals(request.getUserId())),
        loan -> {
          Map<String, String> parameters = new HashMap<>();
          parameters.put("itemId", request.getItemId());
          parameters.put("userId", request.getUserId());
          parameters.put("loanId", loan.getId());

          String message = "This requester currently has this item on loan.";

          return singleValidationError(new ValidationError(message, parameters));
        })
        .map(loan -> requestAndRelatedRecords));
  }
}
