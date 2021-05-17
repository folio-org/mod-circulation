package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.support.results.Result;

public class RequestLoanValidator {
  private final LoanRepository loanRepository;

  public RequestLoanValidator(LoanRepository loanRepository) {
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenUserHasAlreadyBeenLoanedItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    return loanRepository.findOpenLoanForRequest(request).thenApply(loanResult -> loanResult
        .failWhen(loan -> of(() -> loan != null && loan.getUserId().equals(request.getUserId())),
            loan -> singleValidationError(
                "ui-requests.mod-circulation.requesterHasItemOnLoan",
                "This requester currently has this item on loan.",
                "itemId", request.getItemId(),
                "userId", request.getUserId(),
                "loanId", loan.getId()))
        .map(loan -> requestAndRelatedRecords));
  }
}
