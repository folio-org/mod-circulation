package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.domain.LoanAction.CHECKED_OUT_THROUGH_OVERRIDE;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.http.server.InsufficientOverridePermissionsError;
import org.folio.circulation.support.results.Result;

public abstract class OverrideValidator implements LoanValidator {
  protected static final String OKAPI_PERMISSIONS = "x-okapi-permissions";
  private final String comment;

  public OverrideValidator(String comment) {
    this.comment = comment;
  }

  @Override
  public CompletableFuture<Result<LoanAndRelatedRecords>> validate(LoanAndRelatedRecords records) {
    return ofAsync(() -> records)
      .thenCompose(result -> result.failAfter(relatedRecords -> isOverridingForbidden(),
        relatedRecords -> singleValidationError(
          new InsufficientOverridePermissionsError(null, null))))
      .thenCompose(result -> result.after(this::setLoanAction));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> setLoanAction(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    Loan loan = loanAndRelatedRecords.getLoan();
    loan.changeAction(CHECKED_OUT_THROUGH_OVERRIDE);
    loan.changeActionComment(comment);

    return ofAsync(() -> loanAndRelatedRecords);
  }

  protected abstract CompletableFuture<Result<Boolean>> isOverridingForbidden();
}
