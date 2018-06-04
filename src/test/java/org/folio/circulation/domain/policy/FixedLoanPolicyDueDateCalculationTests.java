package org.folio.circulation.domain.policy;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.UUID;

import static api.support.matchers.FailureMatcher.isValidationFailure;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class FixedLoanPolicyDueDateCalculationTests {
  @Test
  public void shouldUseOnlyScheduleAvailableWhenLoanDateFits() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<DateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan);

    assertThat(calculationResult.value(), is(new DateTime(2018, 12, 31, 23, 59, 59,
      DateTimeZone.UTC)));
  }

  @Test
  public void shouldFailWhenLoanDateIsBeforeOnlyScheduleAvailable() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    DateTime loanDate = new DateTime(2017, 12, 30, 14, 32, 21, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, isValidationFailure(
      "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy. " +
        "Please review \"Example Fixed Schedule Loan Policy\" before retrying"));
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

    DateTime loanDate = new DateTime(2019, 1, 1, 8, 10, 45, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, isValidationFailure(
      "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy. " +
        "Please review \"Example Fixed Schedule Loan Policy\" before retrying"));
  }

  @Test
  public void shouldUseFirstScheduleAvailableWhenLoanDateFits() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeMonth(2018, 1);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(expectedSchedule)
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    DateTime loanDate = new DateTime(2018, 1, 8, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<DateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan);

    assertThat(calculationResult.value(), is(expectedSchedule.due));
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

    DateTime loanDate = new DateTime(2018, 2, 27, 16, 23, 43, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<DateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan);

    assertThat(calculationResult.value(), is(expectedSchedule.due));
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

    DateTime loanDate = new DateTime(2018, 3, 12, 7, 15, 23, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<DateTime> calculationResult = loanPolicy
      .calculateInitialDueDate(loan);

    assertThat(calculationResult.value(), is(expectedSchedule.due));
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

    DateTime loanDate = new DateTime(2017, 12, 30, 14, 32, 21, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, isValidationFailure(
      "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy. " +
        "Please review \"Example Fixed Schedule Loan Policy\" before retrying"));
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

    DateTime loanDate = new DateTime(2018, 4, 1, 6, 34, 21, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, isValidationFailure(
      "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy. " +
        "Please review \"Example Fixed Schedule Loan Policy\" before retrying"));
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

    DateTime loanDate = new DateTime(2018, 2, 18, 6, 34, 21, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, isValidationFailure(
      "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy. " +
        "Please review \"Example Fixed Schedule Loan Policy\" before retrying"));
  }

  @Test
  public void shouldFailWhenNoSchedulesDefined() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder().create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    assertThat(result, isValidationFailure(
      "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy. " +
        "Please review \"Example Fixed Schedule Loan Policy\" before retrying"));
  }

  @Test
  public void shouldFailWhenSchedulesCollectionIsNull() {
    final FixedScheduleDueDateStrategy calculator =
      new FixedScheduleDueDateStrategy(UUID.randomUUID().toString(),
        "Example Fixed Schedule Loan Policy", null);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = loanFor(loanDate);

    final HttpResult<DateTime> result = calculator.calculateInitialDueDate(loan);

    assertThat(result, isValidationFailure(
      "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy. " +
        "Please review \"Example Fixed Schedule Loan Policy\" before retrying"));
  }

  private Loan loanFor(DateTime loanDate) {
    return new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject();
  }
}
