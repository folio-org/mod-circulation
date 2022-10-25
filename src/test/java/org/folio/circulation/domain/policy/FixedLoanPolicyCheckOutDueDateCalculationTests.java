package org.folio.circulation.domain.policy;

import static api.support.matchers.FailureMatcher.hasValidationFailure;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;

class FixedLoanPolicyCheckOutDueDateCalculationTests {
  @Test
  void shouldUseOnlyScheduleAvailableWhenLoanDateFits() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan, null);

    assertThat(calculationResult.value(), is(ZonedDateTime.of(2018, 12, 31, 23, 59, 59, 0, UTC)));
  }

  @Test
  void shouldPickCorrectDueDateFromAdjacentSchedulesWithNegativeTimeZoneOffset() {
    /* This is a schedule as seen in the request sent to fixed-due-date-schedule-storage when a
    schedule is created via UI for a tenant in a timezone with -4h offset:
    {
      "from":"2020-01-01T00:00:00-04:00",
      "to":"2020-02-02T23:59:59-04:00",
      "due":"2020-02-02T23:59:59-04:00"
    }, {
      "from":"2020-10-03T00:00:00-04:00",
      "to":"2020-10-04T23:59:59-04:00",
      "due":"2020-10-04T23:59:59-04:00"
    }
    But the schedules are actually persisted in UTC with dates and hours adjusted accordingly as
    shown below. Note how end dates and due dates roll over to the next day. */

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
        .fixed(UUID.randomUUID())
        .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(new FixedDueDateSchedule(
          ZonedDateTime.of(2020, 1, 3, 4, 0, 0, 0, UTC),
          ZonedDateTime.of(2020, 1, 5, 3, 59, 59, 0, UTC),
          ZonedDateTime.of(2020, 1, 5, 3, 59, 59, 0, UTC))) // expected due date
        .addSchedule(new FixedDueDateSchedule(
          ZonedDateTime.of(2020, 1, 1, 4, 0, 0, 0, UTC),
          ZonedDateTime.of(2020, 1, 3, 3, 59, 59, 0, UTC),
          ZonedDateTime.of(2020, 1, 3, 3, 59, 59, 0, UTC)))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2020, 1, 3, 12, 0, 0, 0, ZoneOffset.ofHours(-4));

    final Result<ZonedDateTime> dueDateResult =
      loanPolicy.calculateInitialDueDate(loanFor(loanDate), null);

    assertThat(dueDateResult.succeeded(), is(true));
    assertThat(dueDateResult.value(), is(ZonedDateTime.of(2020, 1, 5, 3, 59, 59, 0, UTC)));
  }

  @Test
  void shouldFailWhenLoanDateIsBeforeOnlyScheduleAvailable() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2017, 12, 30, 14, 32, 21, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

    assertThat(result, hasValidationFailure(
      "loan date falls outside of the date ranges in the loan policy"));
  }

  @Test
  void shouldFailWhenLoanDateIsAfterOnlyScheduleAvailable() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2019, 1, 1, 8, 10, 45, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

    assertThat(result, hasValidationFailure(
      "loan date falls outside of the date ranges in the loan policy"));
  }

  @Test
  void shouldUseFirstScheduleAvailableWhenLoanDateFits() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeMonth(2018, 1);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(expectedSchedule)
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 1, 8, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan, null);

    assertThat(calculationResult.value(), is(expectedSchedule.due));
  }

  @Test
  void shouldUseMiddleScheduleAvailableWhenLoanDateFits() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeMonth(2018, 2);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(expectedSchedule)
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 2, 27, 16, 23, 43, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan, null);

    assertThat(calculationResult.value(), is(expectedSchedule.due));
  }

  @Test
  void shouldUseLastScheduleAvailableWhenLoanDateFits() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeMonth(2018, 3);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .addSchedule(expectedSchedule)
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 12, 7, 15, 23, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan, null);

    assertThat(calculationResult.value(), is(expectedSchedule.due));
  }

  @Test
  void shouldFailWhenLoanDateIsBeforeAllSchedules() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2017, 12, 30, 14, 32, 21, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

    assertThat(result, hasValidationFailure(
      "loan date falls outside of the date ranges in the loan policy"));
  }

  @Test
  void shouldFailWhenLoanDateIsAfterAllSchedules() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 4, 1, 6, 34, 21, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

    assertThat(result, hasValidationFailure(
      "loan date falls outside of the date ranges in the loan policy"));
  }

  @Test
  void shouldFailWhenLoanDateIsInBetweenSchedules() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 2, 18, 6, 34, 21, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

    assertThat(result, hasValidationFailure(
      "loan date falls outside of the date ranges in the loan policy"));
  }

  @Test
  void shouldFailWhenNoSchedulesDefined() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder().create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

    assertThat(result, hasValidationFailure(
      "loan date falls outside of the date ranges in the loan policy"));
  }

  @Test
  void shouldFailWhenSchedulesCollectionIsNull() {
    final FixedScheduleCheckOutDueDateStrategy calculator =
      new FixedScheduleCheckOutDueDateStrategy(UUID.randomUUID().toString(),
        "Example Fixed Schedule Loan Policy", null, ValidationError::new);

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> result = calculator.calculateDueDate(loan);

    assertThat(result.failed(), is(true));
    assertThat(result, hasValidationFailure(
      "loan date falls outside of the date ranges in the loan policy"));
  }

  @Test
  void shouldFailWhenNoSchedules() {
    final FixedScheduleCheckOutDueDateStrategy calculator =
      new FixedScheduleCheckOutDueDateStrategy(UUID.randomUUID().toString(),
        "Example Fixed Schedule Loan Policy", new NoFixedDueDateSchedules(), ValidationError::new);

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate);

    final Result<ZonedDateTime> result = calculator.calculateDueDate(loan);

    assertThat(result, hasValidationFailure(
      "loan date falls outside of the date ranges in the loan policy"));
  }

  private Loan loanFor(ZonedDateTime loanDate) {
    return new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject();
  }
}
