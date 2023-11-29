package org.folio.circulation.domain.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.RemindersPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

public class RenewalOfItemsWithReminderFeesValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public CompletableFuture<Result<RenewalContext>> blockRenewalIfReminderFeesExistAndDisallowRenewalWithReminders(
    RenewalContext renewalContext) {
    return completedFuture(blockRenewalIfRuledByRemindersFeePolicy(renewalContext));
  }

  Result<RenewalContext> blockRenewalIfRuledByRemindersFeePolicy(RenewalContext renewalContext) {
    log.debug("blockRenewalIfRuledByRemindersFeePolicy:: renewalContext: {}", renewalContext);

    Loan loan = renewalContext.getLoan();
    Integer lastFeeBilledCount = loan.getLastReminderFeeBilledNumber();

    OverdueFinePolicy overdueFinePolicy = loan.getOverdueFinePolicy();
    RemindersPolicy remindersPolicy = overdueFinePolicy.getRemindersPolicy();
    Boolean allowRenewalWhenThereAreReminders = remindersPolicy.getAllowRenewalOfItemsWithReminderFees();

    if ((lastFeeBilledCount != null && lastFeeBilledCount > 0) && Boolean.FALSE.equals(allowRenewalWhenThereAreReminders)) {
      String reason = "Patron has too many overdue items for their group! They need to renew or return them!";
      log.info(String.format("createBlockedRenewalDueToReminderFeesPolicyError:: %s", reason));
      String message = "Patron's fee/fine balance exceeds the limit for their patron group! Pay up!";
      return failed(createBlockedRenewalDueToReminderFeesPolicyError(message, reason));
    } else {
      return succeeded(renewalContext);
    }
  }

  private HttpFailure createBlockedRenewalDueToReminderFeesPolicyError(String message, String reason) {
    log.debug("createBlockedRenewalDueToReminderFeesPolicyError:: parameters message: {}, reason: {}", message,
      reason);

    return singleValidationError(new ValidationError(message, "reason", reason));
  }
}
