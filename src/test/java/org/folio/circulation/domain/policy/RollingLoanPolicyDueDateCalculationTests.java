package org.folio.circulation.domain.policy;

import api.support.builders.*;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static api.support.matchers.FailureMatcher.isValidationFailure;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class RollingLoanPolicyDueDateCalculationTests {

  @Test
  @Parameters({
    "1",
    "8",
    "12",
    "15"
  })
  public void shouldApplyMonthlyRollingPolicy(int duration) {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.months(duration))
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> calculationResult = loanPolicy
      .calculate(loan);

    assertThat(calculationResult.value(), is(loanDate.plusMonths(duration)));
  }

  @Test
  @Parameters({
    "1",
    "2",
    "3",
    "4",
    "5"
  })
  public void shouldApplyWeeklyRollingPolicy(int duration) {

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.weeks(duration))
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> calculationResult = loanPolicy
      .calculate(loan);

    assertThat(calculationResult.value(), is(loanDate.plusWeeks(duration)));
  }

  @Test
  @Parameters({
    "1",
    "7",
    "14",
    "12",
    "30",
    "100"
  })
  public void shouldApplyDailyRollingPolicy(int duration) {

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.days(duration))
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> calculationResult = loanPolicy
      .calculate(loan);

    assertThat(calculationResult.value(), is(loanDate.plusDays(duration)));
  }

  @Test
  @Parameters({
    "2",
    "5",
    "30",
    "45",
    "60",
    "24"
  })
  public void shouldApplyHourlyRollingPolicy(int duration) {

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.hours(duration))
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> calculationResult = loanPolicy
      .calculate(loan);

    assertThat(calculationResult.value(), is(loanDate.plusHours(duration)));
  }

  @Test
  @Parameters({
    "1",
    "5",
    "30",
    "60",
    "200"
  })
  public void shouldApplyMinuteIntervalRollingPolicy(int duration) {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.minutes(duration))
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> calculationResult = loanPolicy
      .calculate(loan);

    assertThat(calculationResult.value(), is(loanDate.plusMinutes(duration)));
  }

  @Test
  public void shouldFailForNonRollingProfile() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .withLoansProfile("Unknown profile")
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> result = loanPolicy.calculate(loan);

    assertThat(result, isValidationFailure(
      "Loans policy cannot be applied - Unrecognised profile - Unknown profile"));
  }

  @Test
  public void shouldFailForUnrecognisedInterval() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(new Period(5, "Unknown"))
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> result = loanPolicy.calculate(loan);

    assertThat(result, isValidationFailure(
      "Loans policy cannot be applied - Unrecognised interval - Unknown"));
  }

  @Test
  public void shouldLimitDueDateWhenWithinDueDateLimitSchedule() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.months(1))
      .limitedBySchedule(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3,
          new DateTime(2018, 4, 10, 23, 59, 59, DateTimeZone.UTC)))
        .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> result = loanPolicy.calculate(loan);

    assertThat(result.value(), is(new DateTime(2018, 4, 10, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void shouldNotLimitDueDateWhenWithinDueDateLimitScheduleButInitialDateIsSooner() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.weeks(2))
      .limitedBySchedule(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    DateTime loanDate = new DateTime(2018, 3, 11, 16, 21, 43, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> result = loanPolicy.calculate(loan);

    assertThat(result.value(), is(new DateTime(2018, 3, 25, 16, 21, 43, DateTimeZone.UTC)));
  }

  @Test
  public void shouldFailWhenNotWithinOneOfProvidedDueDateLimitSchedules() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .withName("One Month")
      .rolling(Period.months(1))
      .limitedBySchedule(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 5))
        .create());

    DateTime loanDate = new DateTime(2018, 4, 3, 9, 25, 43, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> result = loanPolicy.calculate(loan);

    assertThat(result, isValidationFailure(
      "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy. " +
        "Please review One Month before retrying checking out"));
  }

  @Test
  public void shouldFailWhenNoDueDateLimitSchedules() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.months(1))
      .withName("One Month")
      .limitedBySchedule(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder().create());

    DateTime loanDate = new DateTime(2018, 4, 3, 9, 25, 43, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> result = loanPolicy.calculate(loan);

    assertThat(result, isValidationFailure(
      "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy. " +
        "Please review One Month before retrying checking out"));
  }
}
