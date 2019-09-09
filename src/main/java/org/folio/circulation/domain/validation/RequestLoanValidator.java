package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;

public class RequestLoanValidator {
  private final LoanRepository loanRepository;

  public RequestLoanValidator(LoanRepository loanRepository) {
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenUserHasAlreadyBeenLoanedItem(
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
