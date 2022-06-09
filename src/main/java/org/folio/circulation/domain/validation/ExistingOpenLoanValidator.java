package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.http.server.RepresentationError.ITEM_HAS_OPEN_LOAN;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.results.Result;
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
        v -> existingOpenLoanErrorFunction.apply(ITEM_HAS_OPEN_LOAN.getDescription())))
      .thenApply(result -> result.map(v -> loanAndRelatedRecords));
  }
}
