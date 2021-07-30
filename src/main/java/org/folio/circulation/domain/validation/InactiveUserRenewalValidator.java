package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.User;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

public class InactiveUserRenewalValidator {
  private final String isInactiveMessage = "Cannot check out to inactive user";
  private final String isInactiveReason = "User is inactive.";
  private final String cannotDetermineMessage = "Cannot determine if user is active.";
  private final String cannotDetermineReason = "Cannot determine if user active.";
  private final WebContext context;
  private final UserRepository repository;

  public InactiveUserRenewalValidator(WebContext context, UserRepository userRepository) {
    this.context = context;
    this.repository = userRepository;
  }

  public CompletableFuture<Result<RenewalContext>> refuseWhenPatronIsInactive(
    RenewalContext renewalContext) {
    return repository.getUser(context.getUserId())
      .thenApply(u -> refuseWhenUserIsInactive(u.value(), renewalContext));
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