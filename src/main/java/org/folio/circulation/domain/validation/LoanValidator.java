package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.results.Result;

public class LoanValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private LoanValidator() {}

  public static Result<Loan> refuseWhenLoanIsClosed(Result<Loan> loanResult) {
    log.debug("refuseWhenLoanIsClosed:: parameters loanResult={}",
      () -> resultAsString(loanResult));

    return loanResult.failWhen(
      loan -> succeeded(loan.isClosed()),
      loan -> singleValidationError("Loan is closed", "loanId", loan.getId())
    );
  }
}
