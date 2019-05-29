package org.folio.circulation.domain.policy;

import static api.support.matchers.FailureMatcher.hasValidationFailure;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
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
      .calculateInitialDueDate(loan, null);

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
      .calculateInitialDueDate(loan, null);

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
      .calculateInitialDueDate(loan, null);

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
      .calculateInitialDueDate(loan, null);

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
      .calculateInitialDueDate(loan, null);

    assertThat(calculationResult.value(), is(loanDate.plusMinutes(duration)));
  }

  @Test
  public void shouldApplyAlternateScheduleWhenQueuedRequestIsHoldAndRolling() {
    final Period alternateCheckoutLoanPeriod = Period.from(2, "Weeks");
    final DateTime systemTime = DateTime.now();

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.months(1))
      .withAlternateCheckoutLoanPeriod(alternateCheckoutLoanPeriod)
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(systemTime.getYear()))
        .create());

    Item item = Item.from(
      new ItemBuilder()
        .checkOut()
        .withId(UUID.randomUUID())
        .create());
    Loan loan = Loan.from(
      new LoanBuilder()
        .withItemId(UUID.fromString(item.getItemId()))
        .withLoanDate(systemTime)
        .create());

    Request requestOne = Request.from(new RequestBuilder()
      .withId(UUID.randomUUID())
      .withPosition(1)
      .create());

    Request requestTwo = Request.from(new RequestBuilder()
      .withId(UUID.randomUUID())
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .hold()
      .withItemId(UUID.fromString(loan.getItemId()))
      .withPosition(2)
      .create());

    RequestQueue requestQueue = new RequestQueue(asList(requestOne, requestTwo));

    Result<DateTime> initialDueDateResult = loanPolicy.calculateInitialDueDate(loan, requestQueue);

    String key = "alternateCheckoutLoanPeriod";
    DateTime expectedDueDate = alternateCheckoutLoanPeriod.addTo(
        systemTime,
        () -> errorForLoanPeriod(format("the \"%s\" is not recognized", key)),
        interval -> errorForLoanPeriod(format("the interval \"%s\" in \"%s\" is not recognized", interval, key)),
        dur -> errorForLoanPeriod(format("the duration \"%s\" in \"%s\" is invalid", dur, key)))
          .value();
    Long result = initialDueDateResult.value().getMillis();
    Long expected = expectedDueDate.getMillis();
    assertThat(result.doubleValue(), closeTo(expected.doubleValue(), 10000));
  }

  private ValidationError errorForLoanPeriod(String reason) {
    Map<String, String> parameters = new HashMap<String, String>();
    return new ValidationError(reason, parameters);
  }

  @Test
  public void shouldFailForUnrecognisedInterval() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.from(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

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

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

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

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

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

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

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

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

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

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

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

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

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

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

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

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

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
