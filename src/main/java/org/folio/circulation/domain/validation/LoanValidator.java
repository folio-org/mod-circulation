package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Result;

public class LoanValidator {

  private LoanValidator() {}

  public static Result<Loan> refuseWhenLoanIsClosed(Result<Loan> loanResult) {
    return loanResult.failWhen(
      loan -> succeeded(loan.isClosed()),
      loan -> singleValidationError("Loan is closed", "id", loan.getId())
    );
  }
}
