package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.ofAsync;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ExistingOpenLoanValidator {
  private final Function<String, ValidationErrorFailure> existingOpenLoanErrorFunction;
  private final LoanRepository loanRepository;

  public ExistingOpenLoanValidator(
    LoanRepository loanRepository,
    Function<String, ValidationErrorFailure> existingOpenLoanErrorFunction) {

    this.loanRepository = loanRepository;
    this.existingOpenLoanErrorFunction = existingOpenLoanErrorFunction;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenHasOpenLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return ofAsync(() -> loanAndRelatedRecords.getLoan().getItemId())
      .thenComposeAsync(result -> result.failAfter(loanRepository::hasOpenLoan,
        v -> existingOpenLoanErrorFunction.apply(
          "Cannot check out item that already has an open loan")))
      .thenApply(result -> result.map(v -> loanAndRelatedRecords));
  }
}
