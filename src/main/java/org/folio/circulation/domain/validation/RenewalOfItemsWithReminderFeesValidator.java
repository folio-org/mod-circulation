package org.folio.circulation.domain.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

public class RenewalOfItemsWithReminderFeesValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public CompletableFuture<Result<RenewalContext>> blockRenewalIfReminderFeesExistAndDisallowRenewalWithReminders(
    RenewalContext renewalContext) {
    return completedFuture(blockRenewalIfRuledByRemindersFeePolicy(renewalContext));
  }

  Result<RenewalContext> blockRenewalIfRuledByRemindersFeePolicy(RenewalContext renewalContext) {
    log.debug("blockRenewalIfRuledByRemindersFeePolicy:: parameters: renewalContext: {}", renewalContext);
    BlockOverrides overrides = BlockOverrides.from(getObjectProperty(renewalContext.getRenewalRequest(), "overrideBlocks"));
    final boolean overrideRenewalBlock = overrides.getRenewalBlockOverride().isRequested();
    final boolean overrideRenewalBlockDueDateRequired = overrides.getRenewalDueDateRequiredBlockOverride().isRequested();
    if (overrideRenewalBlock || overrideRenewalBlockDueDateRequired) {
      return succeeded(renewalContext);
    } else if (renewalBlockedDueToReminders(renewalContext)) {
      return failed(createBlockedRenewalDueToReminderFeesPolicyError());
    } else {
      return succeeded(renewalContext);
    }
  }

  private HttpFailure createBlockedRenewalDueToReminderFeesPolicyError() {
    String reasonAndMessage = "Renewals not allowed for loans with reminders.";
    log.debug("createBlockedRenewalDueToReminderFeesPolicyError");
    return singleValidationError(new ValidationError(reasonAndMessage, "reason", reasonAndMessage));
  }

  private static boolean renewalBlockedDueToReminders(RenewalContext renewalContext) {
    Loan loan = renewalContext.getLoan();
    return loanHasReminders(loan) && policyBlocksRenewalWithReminders(loan);
  }
  private static Boolean loanHasReminders(Loan loan) {
    return loan.getLastReminderFeeBilledNumber() != null && loan.getLastReminderFeeBilledNumber() > 0;
  }
  private static boolean policyBlocksRenewalWithReminders (Loan loan) {
    OverdueFinePolicy overdueFinePolicy = loan.getOverdueFinePolicy();
    Boolean allowRenewalWithReminders = overdueFinePolicy.getRemindersPolicy().getAllowRenewalOfItemsWithReminderFees();
    return Boolean.FALSE.equals(allowRenewalWithReminders);
  }
}
