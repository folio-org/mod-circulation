package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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

    return loanRepository.hasOpenLoan(loanAndRelatedRecords.getLoan().getItemId())
      .thenApply(r -> r.failWhen(r, existingOpenLoanErrorFunction.apply(
          "Cannot check out item that already has an open loan")))
      .thenApply(r -> r.map(v -> loanAndRelatedRecords));
  }
}
