package org.folio.circulation.resources.renewal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.junit.jupiter.api.Test;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;

class BulkRenewalEligibilityFilterTest {

  private static final ZoneId UTC = ZoneId.of("UTC");

  // ── Rule 1: automaticRenewal disabled ───────────────────────────────────────

  @Test
  void shouldExcludeLoanWhenAutomaticRenewalIsDisabled() {
    Loan loan = loan(dueInDays(2));
    LoanPolicy policy = policy(false, 3, null);

    AutomaticRenewalEligibilityResult result =
      BulkRenewalEligibilityFilter.filter(List.of(loan), Map.of(loan.getId(), policy),
        Map.of(), UTC);

    assertTrue(result.eligible().isEmpty());
    assertTrue(result.holdRecallFailures().isEmpty());
  }

  @Test
  void shouldExcludeLoanWhenAutomaticRenewalConfigIsAbsent() {
    // LoanPolicyBuilder with no automaticRenewal set → isAutomaticRenewalEnabled() == false
    Loan loan = loan(dueInDays(2));
    LoanPolicy policyWithNoAutoRenewal = LoanPolicy.from(new LoanPolicyBuilder().create());

    AutomaticRenewalEligibilityResult result =
      BulkRenewalEligibilityFilter.filter(List.of(loan), Map.of(loan.getId(), policyWithNoAutoRenewal),
        Map.of(), UTC);

    assertTrue(result.eligible().isEmpty());
    assertTrue(result.holdRecallFailures().isEmpty());
  }

  // ── Rule 2: eligibility window ──────────────────────────────────────────────

  @Test
  void shouldIncludeLoanWhenDueWithinWindow() {
    Loan loan = loan(dueInDays(3)); // exactly at boundary
    LoanPolicy enabledPolicy = policy(true, 3, null);

    AutomaticRenewalEligibilityResult result =
      BulkRenewalEligibilityFilter.filter(List.of(loan), Map.of(loan.getId(), enabledPolicy),
        Map.of(), UTC);

    assertEquals(List.of(loan), result.eligible());
    assertTrue(result.holdRecallFailures().isEmpty());
  }

  @Test
  void shouldExcludeLoanWhenOutsideWindow() {
    Loan loan = loan(dueInDays(5)); // 5 days away, window is 3
    LoanPolicy narrowWindow = policy(true, 3, null);

    AutomaticRenewalEligibilityResult result =
      BulkRenewalEligibilityFilter.filter(List.of(loan), Map.of(loan.getId(), narrowWindow),
        Map.of(), UTC);

    assertTrue(result.eligible().isEmpty());
    assertTrue(result.holdRecallFailures().isEmpty());
  }

  @Test
  void shouldExcludeWhenWindowConfigIsNull() {
    Loan loan = loan(dueInDays(1));
    LoanPolicy noWindowPolicy = policy(true, null, null); // no window configured

    AutomaticRenewalEligibilityResult result =
      BulkRenewalEligibilityFilter.filter(List.of(loan), Map.of(loan.getId(), noWindowPolicy),
        Map.of(), UTC);

    assertTrue(result.eligible().isEmpty());
    assertTrue(result.holdRecallFailures().isEmpty());
  }

  // ── Rule 3: hold/recall blocking ────────────────────────────────────────────

  @Test
  void shouldExcludeWithFailureWhenRecallBlocksRenewal() {
    UUID itemId = UUID.randomUUID();
    Loan loan = loanWithItem(dueInDays(2), itemId);
    LoanPolicy wideWindow = policy(true, 7, null);

    var recall = new RequestBuilder()
      .withItemId(itemId)
      .withRequestType("Recall")
      .withStatus(RequestBuilder.OPEN_NOT_YET_FILLED)
      .create();

    RequestQueue queue = new RequestQueue(List.of(
      org.folio.circulation.domain.Request.from(recall)));

    AutomaticRenewalEligibilityResult result =
      BulkRenewalEligibilityFilter.filter(List.of(loan), Map.of(loan.getId(), wideWindow),
        Map.of(loan.getId(), queue), UTC);

    assertTrue(result.eligible().isEmpty());
    assertEquals(1, result.holdRecallFailures().size());
    assertTrue(result.holdRecallFailures().containsKey(loan.getId()));
  }

  @Test
  void shouldExcludeWithFailureWhenNonRenewableHoldBlocksRenewal() {
    UUID itemId = UUID.randomUUID();
    Loan loan = loanWithItem(dueInDays(2), itemId);
    LoanPolicy holdNotRenewable = policyWithHoldNotRenewable(true, 7);

    var hold = new RequestBuilder()
      .withItemId(itemId)
      .withRequestType("Hold")
      .withStatus(RequestBuilder.OPEN_NOT_YET_FILLED)
      .create();

    RequestQueue queue = new RequestQueue(List.of(
      org.folio.circulation.domain.Request.from(hold)));

    AutomaticRenewalEligibilityResult result =
      BulkRenewalEligibilityFilter.filter(List.of(loan), Map.of(loan.getId(), holdNotRenewable),
        Map.of(loan.getId(), queue), UTC);

    assertTrue(result.eligible().isEmpty());
    assertEquals(1, result.holdRecallFailures().size());
  }

  @Test
  void shouldIncludeWhenHoldExistsButPolicyAllowsHoldRenewal() {
    UUID itemId = UUID.randomUUID();
    Loan loan = loanWithItem(dueInDays(2), itemId);
    LoanPolicy holdRenewable = policyWithHoldRenewable(true, 7);

    var hold = new RequestBuilder()
      .withItemId(itemId)
      .withRequestType("Hold")
      .withStatus(RequestBuilder.OPEN_NOT_YET_FILLED)
      .create();

    RequestQueue queue = new RequestQueue(List.of(
      org.folio.circulation.domain.Request.from(hold)));

    AutomaticRenewalEligibilityResult result =
      BulkRenewalEligibilityFilter.filter(List.of(loan), Map.of(loan.getId(), holdRenewable),
        Map.of(loan.getId(), queue), UTC);

    assertEquals(List.of(loan), result.eligible());
  }

  // ── Scenario 6: mixed policies ───────────────────────────────────────────────

  @Test
  void shouldFilterMixedBatchCorrectly() {
    Loan eligible = loan(dueInDays(2));
    Loan disabledPolicyLoan = loan(dueInDays(1));
    Loan outsideWindowLoan = loan(dueInDays(10));

    LoanPolicy enabledPolicy = policy(true, 7, null);
    LoanPolicy disabled = policy(false, 7, null);
    LoanPolicy narrowWindow = policy(true, 3, null);

    AutomaticRenewalEligibilityResult result =
      BulkRenewalEligibilityFilter.filter(
        List.of(eligible, disabledPolicyLoan, outsideWindowLoan),
        Map.of(
          eligible.getId(), enabledPolicy,
          disabledPolicyLoan.getId(), disabled,
          outsideWindowLoan.getId(), narrowWindow),
        Map.of(), UTC);

    assertEquals(List.of(eligible), result.eligible());
    assertTrue(result.holdRecallFailures().isEmpty());
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private static Loan loan(ZonedDateTime dueDate) {
    return new LoanBuilder()
      .withId(UUID.randomUUID())
      .withItemId(UUID.randomUUID())
      .withUserId(UUID.randomUUID())
      .withDueDate(dueDate)
      .asDomainObject();
  }

  private static Loan loanWithItem(ZonedDateTime dueDate, UUID itemId) {
    return new LoanBuilder()
      .withId(UUID.randomUUID())
      .withItemId(itemId)
      .withUserId(UUID.randomUUID())
      .withDueDate(dueDate)
      .asDomainObject();
  }

  private static ZonedDateTime dueInDays(int days) {
    return LocalDate.now(UTC).plusDays(days).atStartOfDay(UTC);
  }

  private static LoanPolicy policy(boolean autoRenewal, Integer firstAttemptDays,
      Integer retryDays) {
    return LoanPolicy.from(new LoanPolicyBuilder()
      .withAutomaticRenewal(autoRenewal)
      .withFirstAutomaticRenewalAttemptDaysBeforeDueDate(firstAttemptDays)
      .withAutomaticRenewalRetryPeriodInDays(retryDays)
      .create());
  }

  private static LoanPolicy policyWithHoldNotRenewable(boolean autoRenewal,
      Integer firstAttemptDays) {
    return LoanPolicy.from(new LoanPolicyBuilder()
      .withAutomaticRenewal(autoRenewal)
      .withFirstAutomaticRenewalAttemptDaysBeforeDueDate(firstAttemptDays)
      .withHolds(null, false, null)  // renewItemsWithRequest = false
      .create());
  }

  private static LoanPolicy policyWithHoldRenewable(boolean autoRenewal,
      Integer firstAttemptDays) {
    return LoanPolicy.from(new LoanPolicyBuilder()
      .withAutomaticRenewal(autoRenewal)
      .withFirstAutomaticRenewalAttemptDaysBeforeDueDate(firstAttemptDays)
      .withHolds(null, true, null)  // renewItemsWithRequest = true
      .create());
  }
}
