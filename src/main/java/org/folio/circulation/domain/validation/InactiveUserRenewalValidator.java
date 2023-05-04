package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.User;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public class InactiveUserRenewalValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final String isInactiveMessage = "Cannot renew loan when user is inactive or expired";
  private final String isInactiveReason = "User is inactive.";
  private final String cannotDetermineMessage = "Cannot determine if user is active.";
  private final String cannotDetermineReason = "Cannot determine if user active.";

  public CompletableFuture<Result<RenewalContext>> refuseWhenPatronIsInactive(
    RenewalContext renewalContext) {
    return completedFuture(refuseWhenUserIsInactive(renewalContext.getLoan().getUser(), renewalContext));
  }

  private Result<RenewalContext> refuseWhenUserIsInactive(User user,
    RenewalContext renewalContext) {

    log.debug("refuseWhenUserIsInactive:: parameters user: {}, renewalContext: {}", user,
      renewalContext);

    if (user.cannotDetermineStatus()) {
      log.info("refuseWhenUserIsInactive:: cannot determine status");
      return failed(createInactiveUserValidationError(cannotDetermineMessage, cannotDetermineReason));
    } else if (user.isInactive()) {
      log.info("refuseWhenUserIsInactive:: user is inactive");
      return failed(createInactiveUserValidationError(isInactiveMessage, isInactiveReason));
    } else {
      return succeeded(renewalContext);
    }
  }

  private HttpFailure createInactiveUserValidationError(String message, String reason) {
    log.debug("createInactiveUserValidationError:: parameters message: {}, reason: {}", message,
      reason);

    return singleValidationError(new ValidationError(message, "reason", reason));
  }
}
