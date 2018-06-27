package org.folio.circulation.domain;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;

public class ExistingOpenLoanValidator {
  private final Function<String, ValidationErrorFailure> existingOpenLoanErrorFunction;
  private final LoanRepository loanRepository;

  public ExistingOpenLoanValidator(
    LoanRepository loanRepository,
    Function<String, ValidationErrorFailure> existingOpenLoanErrorFunction) {

    this.loanRepository = loanRepository;
    this.existingOpenLoanErrorFunction = existingOpenLoanErrorFunction;
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> refuseWhenHasOpenLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    final String itemId = loanAndRelatedRecords.getLoan().getItemId();

    return this.loanRepository.hasOpenLoan(itemId)
      .thenApply(r -> r.next(openLoan -> {
        if(openLoan) {
          return failed(existingOpenLoanErrorFunction.apply(
            "Cannot check out item that already has an open loan"));
        }
        else {
          return succeeded(loanAndRelatedRecords);
        }
      }));
  }
}
