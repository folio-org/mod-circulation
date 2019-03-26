package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.function.Function;

import static org.folio.circulation.support.Result.succeeded;

public class UserNotFoundValidator {
  private final Function<String, ValidationErrorFailure> userNotFoundErrorFunction;

  public UserNotFoundValidator(
    Function<String, ValidationErrorFailure> userNotFoundErrorFunction) {

    this.userNotFoundErrorFunction = userNotFoundErrorFunction;
  }

  public Result<Loan> refuseWhenUserNotFound(Result<Loan> result) {
    return result.failWhen(loan -> succeeded(loan.getUser() == null),
      loan -> userNotFoundErrorFunction.apply(loan.getUserId()));
  }
}
