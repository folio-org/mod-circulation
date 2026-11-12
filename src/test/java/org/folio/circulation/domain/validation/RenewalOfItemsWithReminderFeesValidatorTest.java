package org.folio.circulation.domain.validation;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static api.support.matchers.ResultMatchers.hasValidationError;
import static api.support.matchers.ValidationErrorMatchers.hasCode;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static java.util.Collections.emptyList;
import static org.folio.circulation.support.ErrorCode.RENEWAL_BLOCKED_BY_REMINDER_FEES;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;

class RenewalOfItemsWithReminderFeesValidatorTest {

  private final RenewalOfItemsWithReminderFeesValidator validator = new RenewalOfItemsWithReminderFeesValidator();

  @Test
  void allowRenewalGivenRemindersPolicyAllowsRenewalOfItemsWithReminderFees() {
    final RenewalContext renewalContext = RenewalContext.create(
        createLoan(true, false), null, "no-user")
      .withRequestQueue(new RequestQueue(emptyList()));

    final Result<RenewalContext> result =
      validator.blockRenewalIfRuledByRemindersFeePolicy(renewalContext);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  void allowRenewalGivenRemindersPolicyDisallowsRenewalOfItemsWithReminderFeesAndHasNoReminders() {
    final RenewalContext renewalContext = RenewalContext.create(
        createLoan(false, false), null, "no-user")
      .withRequestQueue(new RequestQueue(emptyList()));

    final Result<RenewalContext> result =
      validator.blockRenewalIfRuledByRemindersFeePolicy(renewalContext);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  void blockRenewalGivenRemindersPolicyDisallowsRenewalOfItemsWithReminderFeesAndHasReminders() {
    final RenewalContext renewalContext = RenewalContext.create(
        createLoan(false, true), null, "no-user")
      .withRequestQueue(new RequestQueue(emptyList()));

    final Result<RenewalContext> result =
      validator.blockRenewalIfRuledByRemindersFeePolicy(renewalContext);

    assertThat(result.failed(), is(true));
    assertThat(result, hasValidationError(allOf(
      hasMessage("Renewals not allowed for loans with reminders."),
      hasCode(RENEWAL_BLOCKED_BY_REMINDER_FEES))));
  }

  private Loan createLoan(Boolean allowRenewalOfItemsWithReminderFees, boolean hasReminders) {
    JsonObject overdueFinePolicyJsonObject = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "Overdue Fine Policy")
      .put("remindersPolicy", new JsonObject()
        .put("allowRenewalOfItemsWithReminderFees", allowRenewalOfItemsWithReminderFees));

    return Loan.from(new JsonObject())
      .withOverdueFinePolicy(OverdueFinePolicy.from(overdueFinePolicyJsonObject))
      .withRemindersLastFeeBilled(hasReminders ? 1 : 0, ClockUtil.getZonedDateTime());
  }
}
