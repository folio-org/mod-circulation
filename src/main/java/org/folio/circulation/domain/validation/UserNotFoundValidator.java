package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.failed;

public class UserNotFoundValidator {
  private final Function<String, ValidationErrorFailure> userNotFoundErrorFunction;

  public UserNotFoundValidator(
    Function<String, ValidationErrorFailure> userNotFoundErrorFunction) {

    this.userNotFoundErrorFunction = userNotFoundErrorFunction;
  }

  public HttpResult<Loan> refuseWhenUserNotFound(
    HttpResult<Loan> result) {

    return result.next(loan -> {
      if(loan.getUser() == null) {
        return failed(userNotFoundErrorFunction.apply(loan.getUserId()));
      }
      else {
        return result;
      }
    });

  }
}
