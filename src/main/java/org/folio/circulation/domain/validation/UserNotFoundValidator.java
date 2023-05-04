package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.server.WebContext;

public final class UserNotFoundValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private UserNotFoundValidator() {}

  public static Result<Loan> refuseWhenUserNotFound(Result<Loan> result) {
    log.debug("refuseWhenUserNotFound:: parameters result: {}", () -> resultAsString(result));

    return result.failWhen(loan -> succeeded(loan.getUser() == null),
      loan -> singleValidationError("user is not found", "userId", loan.getUserId()));
  }

  public static Result<WebContext> refuseWhenLoggedInUserNotPresent(WebContext webContext) {
    log.debug("refuseWhenLoggedInUserNotPresent:: parameters webContext");

    return succeeded(webContext)
      .failWhen(notUsed -> succeeded(StringUtils.isBlank(webContext.getUserId())),
        notUsed -> singleValidationError("No logged-in user present", USER_ID, null));
  }
}
