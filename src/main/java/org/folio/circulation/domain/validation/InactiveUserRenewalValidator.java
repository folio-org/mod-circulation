package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.User;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.http.server.error.ValidationError;
import org.folio.circulation.support.results.Result;

public class InactiveUserRenewalValidator {
  private final String isInactiveMessage = "Cannot renew loan when user is inactive or expired";
  private final String isInactiveReason = "User is inactive.";
  private final String cannotDetermineMessage = "Cannot determine if user is active.";
  private final String cannotDetermineReason = "Cannot determine if user active.";

  public CompletableFuture<Result<RenewalContext>> refuseWhenPatronIsInactive(
    RenewalContext renewalContext) {
    return completedFuture(refuseWhenUserIsInactive(renewalContext.getLoan().getUser(), renewalContext));
  }

  private Result<RenewalContext> refuseWhenUserIsInactive(
    User user, RenewalContext renewalContext) {

    if (user.cannotDetermineStatus()) {
      return failed(createInactiveUserValidationError(cannotDetermineMessage, cannotDetermineReason));
    } else if (user.isInactive()) {
      return failed(createInactiveUserValidationError(isInactiveMessage, isInactiveReason));
    } else {
      return succeeded(renewalContext);
    }
  }

  private HttpFailure createInactiveUserValidationError(String message, String reason) {
    return singleValidationError(new ValidationError(message, "reason", reason));
  }
}
