package org.folio.circulation.domain;

import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;

public class RequestLoanService {
  final RequestRepository requestRepository;
  final RequestPolicyRepository requestPolicyRepository;
  final LoanRepository loanRepository;
  final UpdateItem updateItem;
  final UpdateLoan updateLoan;
  final UpdateLoanActionHistory updateLoanActionHistory;

  public RequestLoanService(RequestRepository requestRepository, RequestPolicyRepository requestPolicyRepository,
      LoanRepository loanRepository, UpdateItem updateItem, UpdateLoan updateLoan,
      UpdateLoanActionHistory updateLoanActionHistory) {
    this.requestRepository = requestRepository;
    this.requestPolicyRepository = requestPolicyRepository;
    this.loanRepository = loanRepository;
    this.updateItem = updateItem;
    this.updateLoan = updateLoan;
    this.updateLoanActionHistory = updateLoanActionHistory;
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenUserHasAlreadyBeenLoanedItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    return loanRepository.findOpenLoanForRequest(request).thenApply(loanResult -> loanResult
      .failWhen(loan -> of(() -> loan != null && loan.getUserId().equals(request.getUserId())), loan -> {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("itemId", request.getItemId());
        parameters.put("userId", request.getUserId());
        parameters.put("loanId", loan.getId());

        String message = "This requester currently has this item on loan.";

        return singleValidationError(new ValidationError(message, parameters));
      }).map(loan -> requestAndRelatedRecords));
  }
}
