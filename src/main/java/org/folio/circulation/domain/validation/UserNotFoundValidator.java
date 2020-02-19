package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.WebContext;

public final class UserNotFoundValidator {

  private UserNotFoundValidator() {}

  public static Result<Loan> refuseWhenUserNotFound(Result<Loan> result) {
    return result.failWhen(loan -> succeeded(loan.getUser() == null),
      loan -> singleValidationError("user is not found", "userId", loan.getUserId()));
  }

  public static Result<WebContext> refuseWhenLoggedInUserNotPresent(WebContext webContext) {
    return succeeded(webContext)
      .failWhen(notUsed -> succeeded(StringUtils.isBlank(webContext.getUserId())),
        notUsed -> singleValidationError("No logged-in user present", USER_ID, null));
  }
}
