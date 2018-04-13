package org.folio.circulation.domain;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.UUID;

import static api.support.matchers.FailureMatcher.isValidationFailure;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class FixedDueDateCalculationTests {
  @Test
  public void shouldUseOnlyScheduleAvailableWhenLoanDateFits() {
    LoanPolicy loanPolicy = new LoanPolicy(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedule(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> calculationResult = new DueDateCalculation()
      .calculate(loan, loanPolicy);

    assertThat(calculationResult.value(), is(new DateTime(2018, 12, 31, 23, 59, 59,
      DateTimeZone.UTC)));
  }

  @Test
  public void shouldFailWhenLoanDateIsBeforeOnlyScheduleAvailable() {
    LoanPolicy loanPolicy = new LoanPolicy(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedule(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    DateTime loanDate = new DateTime(2017, 12, 30, 14, 32, 21, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> result = new DueDateCalculation().calculate(loan, loanPolicy);

    assertThat(result, isValidationFailure(
      "Loans policy cannot be applied - Loan date is not within a schedule"));
  }

  @Test
  public void shouldFailWhenLoanDateIsAfterOnlyScheduleAvailable() {
    LoanPolicy loanPolicy = new LoanPolicy(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedule(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    DateTime loanDate = new DateTime(2019, 1, 1, 8, 10, 45, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> result = new DueDateCalculation().calculate(loan, loanPolicy);

    assertThat(result, isValidationFailure(
      "Loans policy cannot be applied - Loan date is not within a schedule"));
  }

  @Test
  public void shouldFailWhenNoSchedules() {
    LoanPolicy loanPolicy = new LoanPolicy(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedule(new FixedDueDateSchedulesBuilder().create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .create();

    final HttpResult<DateTime> result = new DueDateCalculation().calculate(loan, loanPolicy);

    assertThat(result, isValidationFailure(
      "Loans policy cannot be applied - No schedules for fixed due date loan policy"));
  }
}
