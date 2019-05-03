package org.folio.circulation.domain.policy;

import static api.support.matchers.FailureMatcher.hasValidationFailure;
import static org.hamcrest.CoreMatchers.is;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThat;

import java.util.UUID;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;

public class FixedLoanPolicyRenewalDueDateCalculationTests {

  private static final String EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES =
    "renewal date falls outside of date ranges in fixed loan policy";
  @Test
  public void shouldFailWhenLoanDateIsBeforeOnlyScheduleAvailable() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    Loan loan = existingLoan();

    DateTime renewalDate = new DateTime(2017, 12, 30, 14, 32, 21, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldFailWhenLoanDateIsAfterOnlyScheduleAvailable() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    Loan loan = existingLoan();

    DateTime renewalDate = new DateTime(2019, 1, 1, 8, 10, 45, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldUseOnlyScheduleAvailableWhenLoanDateFits() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    Loan loan = existingLoan();

    DateTime renewalDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result.value().getDueDate(), is(new DateTime(2018, 12, 31, 23, 59, 59,
      DateTimeZone.UTC)));
  }

  @Test
  public void shouldUseFirstScheduleAvailableWhenLoanDateFits() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeMonth(2018, 2);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(expectedSchedule)
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 4))
        .create());

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(new DateTime(2018, 1, 31, 23, 59, 59, DateTimeZone.UTC))
      .asDomainObject();

    DateTime renewalDate = new DateTime(2018, 2, 8, 11, 14, 54, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  public void shouldUseMiddleScheduleAvailableWhenLoanDateFits() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeMonth(2018, 2);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(expectedSchedule)
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    Loan loan = existingLoan();

    DateTime renewalDate = new DateTime(2018, 2, 27, 16, 23, 43, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  public void shouldUseLastScheduleAvailableWhenLoanDateFits() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeMonth(2018, 3);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .addSchedule(expectedSchedule)
        .create());

    Loan loan = existingLoan();

    DateTime renewalDate = new DateTime(2018, 3, 12, 7, 15, 23, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  public void shouldUseAlternateScheduleWhenAvailable() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeYear(2018);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .renewWith(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .create())
      .withAlternateRenewalSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(expectedSchedule)
        .create());

    Loan loan = existingLoan();

    DateTime renewalDate = new DateTime(2018, 2, 5, 14, 22, 32, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  public void shouldApplyAlternateScheduleWhenQueuedRequestIsHoldAndFixed() {
    final FixedDueDateSchedule alternateSchedule = FixedDueDateSchedule.wholeMonth(2018, 1);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create())
      .withAlternateRenewalSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(alternateSchedule)
        .create());

    Loan loan = existingLoan();

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

    assertThat(initialDueDateResult.value(), is(alternateSchedule.due));
  }

  @Test
  public void shouldFailWhenLoanDateIsBeforeAllSchedules() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    Loan loan = existingLoan();

    DateTime renewalDate = new DateTime(2017, 12, 30, 14, 32, 21, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldFailWhenRenewalWouldNotChangeDueDate() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .create());

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(new DateTime(2018, 1, 31, 23, 59, 59, DateTimeZone.UTC))
      .asDomainObject();

    DateTime renewalDate = new DateTime(2018, 1, 3, 8, 12, 32, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result,
      hasValidationFailure("renewal would not change the due date"));
  }

  @Test
  public void shouldFailWhenRenewalWouldMeanEarlierDueDate() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .create());

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(new DateTime(2018, 2, 28, 23, 59, 59, DateTimeZone.UTC))
      .asDomainObject();

    DateTime renewalDate = new DateTime(2018, 1, 3, 8, 12, 32, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result,
      hasValidationFailure("renewal would not change the due date"));
  }

  @Test
  public void shouldFailWhenRenewalWouldMeanEarlierDueDateAndReachedRenewalLimit() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .limitedRenewals(1)
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .create());

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(new DateTime(2018, 1, 31, 23, 59, 59, DateTimeZone.UTC))
      .asDomainObject();

    loan = loanPolicy.renew(loan,
      new DateTime(2018, 2, 1, 11, 23, 43, DateTimeZone.UTC)).value();

    DateTime renewalDate = new DateTime(2018, 3, 5, 8, 12, 32, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));

    assertThat(result, hasValidationFailure(
      "loan at maximum renewal number"));

  }

  @Test
  public void shouldFailWhenLoanDateIsAfterAllSchedules() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    Loan loan = existingLoan();

    DateTime renewalDate = new DateTime(2018, 4, 1, 6, 34, 21, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldFailWhenLoanDateIsInBetweenSchedules() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    Loan loan = existingLoan();

    DateTime renewalDate = new DateTime(2018, 2, 18, 6, 34, 21, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldFailWhenNoSchedulesDefined() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder().create());


    Loan loan = existingLoan();

    DateTime renewalDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    final Result<Loan> result = loanPolicy.renew(loan, renewalDate);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldFailWhenSchedulesCollectionIsNull() {
    DateTime renewalDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    final FixedScheduleRenewalDueDateStrategy calculator =
      new FixedScheduleRenewalDueDateStrategy(UUID.randomUUID().toString(),
        "Example Fixed Schedule Loan Policy", null, renewalDate,
        s -> new ValidationError(s, null, null));

    Loan loan = existingLoan();

    final Result<DateTime> result = calculator.calculateDueDate(loan);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldFailWhenNoSchedules() {
    DateTime renewalDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    final FixedScheduleRenewalDueDateStrategy calculator =
      new FixedScheduleRenewalDueDateStrategy(UUID.randomUUID().toString(),
        "Example Fixed Schedule Loan Policy", new NoFixedDueDateSchedules(),
        renewalDate, s -> new ValidationError(s, null, null));

    Loan loan = existingLoan();

    final Result<DateTime> result = calculator.calculateDueDate(loan);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  private Loan existingLoan() {
    return new LoanBuilder()
      .open()
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(new DateTime(2018, 1, 31, 23, 59, 59, DateTimeZone.UTC))
      .asDomainObject();
  }
}
