package org.folio.circulation.domain.policy;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.Period;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

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

    Loan loan = loanFor(loanDate);

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

    Loan loan = loanFor(loanDate);

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

    Loan loan = loanFor(loanDate);

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

    Loan loan = loanFor(loanDate);

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
  @Ignore("Need to refactor how failure messages are determined")
  public void shouldFailForUnrecognisedInterval() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(new Period(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result, isValidationFailure(
      "Item can't be renewed as the interval \"Unknown\" in the loan policy is not recognised. " +
        "Please review \"Invalid Loan Policy\" before retrying renewal"));
  }

  @Test
  @Ignore("Need to refactor how failure messages are determined")
  public void shouldFailWhenNoPeriodProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(new Period(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create();

    representation.getJsonObject("loansPolicy").remove("period");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result, isValidationFailure(
      "Item can't be renewed as the loan period in the loan policy is not recognised. " +
        "Please review \"Invalid Loan Policy\" before retrying renewal"));
  }

  @Test
  @Ignore("Need to refactor how failure messages are determined")
  public void shouldFailWhenNoPeriodDurationProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(new Period(5, "Weeks"))
      .withName("Invalid Loan Policy")
      .create();

    representation.getJsonObject("loansPolicy").getJsonObject("period").remove("duration");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result, isValidationFailure(
      "Item can't be renewed as the loan period in the loan policy is not recognised. " +
        "Please review \"Invalid Loan Policy\" before retrying renewal"));
  }

  @Test
  @Ignore("Need to refactor how failure messages are determined")
  public void shouldFailWhenNoPeriodIntervalProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(new Period(5, "Weeks"))
      .withName("Invalid Loan Policy")
      .create();

    representation.getJsonObject("loansPolicy").getJsonObject("period").remove("intervalId");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<Loan> result = loanPolicy.renew(loan, DateTime.now());

    assertThat(result, isValidationFailure(
      "Item can't be renewed as the loan period in the loan policy is not recognised. " +
        "Please review \"Invalid Loan Policy\" before retrying renewal"));
  }

  @Test
  @Parameters({
    "0",
    "-1",
  })
  @Ignore("Need to refactor how failure messages are determined")
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
        "Please review \"Invalid Loan Policy\" before retrying renewal", duration)));
  }

  private Loan loanFor(DateTime loanDate) {
    return new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .withDueDate(loanDate.plusWeeks(2))
      .asDomainObject();
  }
}
