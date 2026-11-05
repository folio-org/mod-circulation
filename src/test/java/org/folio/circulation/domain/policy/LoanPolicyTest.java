package org.folio.circulation.domain.policy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;

class LoanPolicyTest {

  @ParameterizedTest
  @CsvSource({
    ", , ",
    "false, , ",
    "true, 5, 2",
    "true, 0, 100",
    "true, 100, 0",
    "true, , 2",
    "true, 5, "
  })
  void readsAutomaticRenewalSettings(Boolean automaticRenewal,
    Integer firstAttemptDays, Integer retryPeriodDays) {

    var policy = new LoanPolicyBuilder()
      .withAutomaticRenewal(automaticRenewal)
      .withFirstAutomaticRenewalAttemptDaysBeforeDueDate(firstAttemptDays)
      .withAutomaticRenewalRetryPeriodInDays(retryPeriodDays)
      .asDomainObject();

    assertEquals(Boolean.TRUE.equals(automaticRenewal),
      policy.isAutomaticRenewalEnabled());
    assertEquals(firstAttemptDays,
      policy.getFirstAutomaticRenewalAttemptDaysBeforeDueDate());
    assertEquals(retryPeriodDays, policy.getAutomaticRenewalRetryPeriodInDays());
  }

  @Test
  void automaticRenewalIsDisabledWhenRenewalsPolicyIsAbsent() {
    var policy = LoanPolicy.from(new JsonObject());

    assertFalse(policy.isAutomaticRenewalEnabled());
    assertNull(policy.getFirstAutomaticRenewalAttemptDaysBeforeDueDate());
    assertNull(policy.getAutomaticRenewalRetryPeriodInDays());
  }

  @Test
  void retainsAutomaticRenewalSettingsWhenSchedulesAreAttached() {
    var policy = new LoanPolicyBuilder()
      .withAutomaticRenewal(true)
      .withFirstAutomaticRenewalAttemptDaysBeforeDueDate(0)
      .withAutomaticRenewalRetryPeriodInDays(100)
      .asDomainObject()
      .withDueDateSchedules(new NoFixedDueDateSchedules())
      .withAlternateRenewalSchedules(new NoFixedDueDateSchedules());

    assertEquals(true, policy.isAutomaticRenewalEnabled());
    assertEquals(0, policy.getFirstAutomaticRenewalAttemptDaysBeforeDueDate());
    assertEquals(100, policy.getAutomaticRenewalRetryPeriodInDays());
  }

  @Test
  void testToString() {
    var loanPolicyId = UUID.randomUUID();
    var loanPolicyName = "Test Loan Policy";
    var representation = new JsonObject()
      .put("id", loanPolicyId)
      .put("name", loanPolicyName);
    var expectedValue = String.format("LoanPolicy(representation={\"id\":\"%s\",\"name\":\"%s\"})",
      loanPolicyId, loanPolicyName);

    assertThat(new LoanPolicy(representation, null, null, null)
      .toString(), is(expectedValue));
  }
}
