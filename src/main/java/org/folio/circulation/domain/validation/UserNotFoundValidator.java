package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.succeeded;

public class UserNotFoundValidator {
  private final Function<String, ValidationErrorFailure> userNotFoundErrorFunction;

  public UserNotFoundValidator(
    Function<String, ValidationErrorFailure> userNotFoundErrorFunction) {

    this.userNotFoundErrorFunction = userNotFoundErrorFunction;
  }

  public HttpResult<Loan> refuseWhenUserNotFound(HttpResult<Loan> result) {
    return result.failWhen(loan -> succeeded(loan.getUser() == null),
      loan -> userNotFoundErrorFunction.apply(loan.getUserId()));
  }
}
