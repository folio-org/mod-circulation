package org.folio.circulation.domain.policy;

import static api.support.matchers.FailureMatcher.hasValidationFailure;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.UUID;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class RollingLoanPolicyCheckOutDueDateCalculationTests {

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

    Loan loan = loanFor(loanDate);

    final Result<DateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan);

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

    Loan loan = loanFor(loanDate);

    final Result<DateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan);

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

    Loan loan = loanFor(loanDate);

    final Result<DateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan);

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

    Loan loan = loanFor(loanDate);

    final Result<DateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan);

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

    Loan loan = loanFor(loanDate);

    final Result<DateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan);

    assertThat(calculationResult.value(), is(loanDate.plusMinutes(duration)));
  }

  @Test
  public void shouldFailForUnrecognisedInterval() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.from(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, hasValidationFailure(
      "the interval \"Unknown\" in the loan policy is not recognised"));
  }

  @Test
  public void shouldFailWhenNoPeriodProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.from(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create();

    representation.getJsonObject("loansPolicy").remove("period");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, hasValidationFailure(
      "the loan period in the loan policy is not recognised"));
  }

  @Test
  public void shouldFailWhenNoPeriodDurationProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.weeks(5))
      .withName("Invalid Loan Policy")
      .create();

    representation.getJsonObject("loansPolicy").getJsonObject("period").remove("duration");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, hasValidationFailure(
      "the loan period in the loan policy is not recognised"));
  }

  @Test
  public void shouldFailWhenNoPeriodIntervalProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.weeks(5))
      .withName("Invalid Loan Policy")
      .create();

    representation.getJsonObject("loansPolicy").getJsonObject("period").remove("intervalId");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, hasValidationFailure(
      "the loan period in the loan policy is not recognised"));
  }

  @Test
  @Parameters({
    "0",
    "-1",
  })
  public void shouldFailWhenDurationIsInvalid(int duration) {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.minutes(duration))
      .withName("Invalid Loan Policy")
      .create();

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, hasValidationFailure(
      String.format(
        "the duration \"%s\" in the loan policy is invalid", duration)));
  }

  @Test
  public void shouldTruncateDueDateWhenWithinDueDateLimitSchedule() {
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

    Loan loan = loanFor(loanDate);

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result.value(), is(new DateTime(2018, 4, 10, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void shouldNotTruncateDueDateWhenWithinDueDateLimitScheduleButInitialDateIsSooner() {
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

    Loan loan = loanFor(loanDate);

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

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

    Loan loan = loanFor(loanDate);

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, hasValidationFailure(
      "loan date falls outside of the date ranges in the loan policy"));
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

    Loan loan = loanFor(loanDate);

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, hasValidationFailure(
      "loan date falls outside of the date ranges in the loan policy"));
  }

  private Loan loanFor(DateTime loanDate) {
    return new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject();
  }
}
