package org.folio.circulation.domain.policy;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.folio.circulation.domain.Loan;
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
public class RollingLoanPolicyRenewalDueDateCalculationTests {

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
      .renewFromSystemDate()
      .unlimitedRenewals()
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate, loanDate.plusMonths(duration));

    DateTime systemDate = new DateTime(2018, 6, 1, 21, 32, 11, DateTimeZone.UTC);

    final HttpResult<Loan> calculationResult = loanPolicy.renew(loan, systemDate);

    assertThat(calculationResult.value().getDueDate(), is(systemDate.plusMonths(duration)));
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
      .renewFromSystemDate()
      .unlimitedRenewals()
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate, loanDate.plusWeeks(duration));

    DateTime systemDate = new DateTime(2018, 6, 1, 21, 32, 11, DateTimeZone.UTC);

    final HttpResult<Loan> calculationResult = loanPolicy.renew(loan, systemDate);

    assertThat(calculationResult.value().getDueDate(), is(systemDate.plusWeeks(duration)));
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
      .renewFromSystemDate()
      .unlimitedRenewals()
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate, loanDate.plusDays(duration));

    DateTime systemDate = new DateTime(2018, 6, 1, 21, 32, 11, DateTimeZone.UTC);

    final HttpResult<Loan> calculationResult = loanPolicy.renew(loan, systemDate);

    assertThat(calculationResult.value().getDueDate(), is(systemDate.plusDays(duration)));
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
      .renewFromSystemDate()
      .unlimitedRenewals()
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate, loanDate.plusHours(duration));

    DateTime systemDate = new DateTime(2018, 6, 1, 21, 32, 11, DateTimeZone.UTC);

    final HttpResult<Loan> calculationResult = loanPolicy.renew(loan, systemDate);

    assertThat(calculationResult.value().getDueDate(), is(systemDate.plusHours(duration)));
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
      .renewFromSystemDate()
      .unlimitedRenewals()
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    DateTime systemDate = new DateTime(2018, 6, 1, 21, 32, 11, DateTimeZone.UTC);

    final HttpResult<Loan> calculationResult = loanPolicy.renew(loan, systemDate);

    assertThat(calculationResult.value().getDueDate(), is(systemDate.plusMinutes(duration)));
  }

  @Test
  public void shouldFailForUnrecognisedInterval() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.from(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result, isValidationFailure(
      "Item can't be renewed as the interval \"Unknown\" in the loan policy is not recognised. " +
        "Please review \"Invalid Loan Policy\" before retrying"));
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

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result, isValidationFailure(
      "Item can't be renewed as the loan period in the loan policy is not recognised. " +
        "Please review \"Invalid Loan Policy\" before retrying"));
  }

  @Test
  public void shouldFailWhenNoPeriodDurationProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.from(5, "Weeks"))
      .withName("Invalid Loan Policy")
      .create();

    representation.getJsonObject("loansPolicy").getJsonObject("period").remove("duration");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result, isValidationFailure(
      "Item can't be renewed as the loan period in the loan policy is not recognised. " +
        "Please review \"Invalid Loan Policy\" before retrying"));
  }

  @Test
  public void shouldFailWhenNoPeriodIntervalProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.from(5, "Weeks"))
      .withName("Invalid Loan Policy")
      .create();

    representation.getJsonObject("loansPolicy").getJsonObject("period").remove("intervalId");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result, isValidationFailure(
      "Item can't be renewed as the loan period in the loan policy is not recognised. " +
        "Please review \"Invalid Loan Policy\" before retrying"));
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

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result, isValidationFailure(
      String.format(
        "Item can't be renewed as the duration \"%s\" in the loan policy is invalid. " +
        "Please review \"Invalid Loan Policy\" before retrying", duration)));
  }

  @Test
  public void shouldTruncateDueDateWhenWithinDueDateLimitSchedule() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.days(15))
      .limitedBySchedule(UUID.randomUUID())
      .renewFromCurrentDueDate()
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3,
          new DateTime(2018, 4, 10, 23, 59, 59, DateTimeZone.UTC)))
        .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate, loanDate.plusDays(15));

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result.value().getDueDate(),
      is(new DateTime(2018, 4, 10, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void shouldNotTruncateDueDateWhenWithinDueDateLimitScheduleButInitialDateIsSooner() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.days(6))
      .limitedBySchedule(UUID.randomUUID())
      .renewFromCurrentDueDate()
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    DateTime loanDate = new DateTime(2018, 3, 11, 16, 21, 43, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate, loanDate.plusDays(6));

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result.value().getDueDate(),
      is(new DateTime(2018, 3, 23, 16, 21, 43, DateTimeZone.UTC)));
  }

  @Test
  public void shouldFailWhenNotWithinOneOfProvidedDueDateLimitSchedules() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .withName("One Month")
      .rolling(Period.months(1))
      .renewFromCurrentDueDate()
      .limitedBySchedule(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 5))
        .create());

    DateTime loanDate = new DateTime(2018, 4, 3, 9, 25, 43, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result, isValidationFailure(
      "Item can't be renewed as the loan date falls outside of the date ranges in the loan policy. " +
        "Please review \"One Month\" before retrying"));
  }

  @Test
  public void shouldFailWhenNoDueDateLimitSchedules() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.months(1))
      .withName("One Month")
      .renewFromCurrentDueDate()
      .limitedBySchedule(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder().create());

    DateTime loanDate = new DateTime(2018, 4, 3, 9, 25, 43, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result, isValidationFailure(
      "Item can't be renewed as the loan date falls outside of the date ranges in the loan policy. " +
        "Please review \"One Month\" before retrying"));
  }

  @Test
  public void shouldFailWhenRenewalWouldNotChangeDueDate() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.weeks(2))
      .withName("Example Rolling Loan Policy")
      .renewFromSystemDate()
      .renewWith(Period.days(3))
      .create());

    final DateTime initialDueDate = new DateTime(2018, 1, 17, 13, 45, 21, DateTimeZone.UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(initialDueDate)
      .asDomainObject();

    DateTime renewalDate = initialDueDate.minusDays(3);

    final HttpResult<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result,
      isValidationFailure("Renewal at this time would not change the due date"));
  }

  @Test
  public void shouldFailWhenRenewalWouldMeanEarlierDueDate() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.weeks(2))
      .withName("Example Rolling Loan Policy")
      .renewFromSystemDate()
      .renewWith(Period.days(3))
      .create());

    final DateTime initialDueDate = new DateTime(2018, 1, 17, 13, 45, 21, DateTimeZone.UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(initialDueDate)
      .asDomainObject();

    DateTime renewalDate = initialDueDate.minusDays(4);

    final HttpResult<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result,
      isValidationFailure("Renewal at this time would not change the due date"));
  }

  private Loan loanFor(DateTime loanDate) {
    return loanFor(loanDate, loanDate.plusWeeks(2));
  }

  private Loan loanFor(DateTime loanDate, DateTime dueDate) {
    return new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .asDomainObject();
  }
}
