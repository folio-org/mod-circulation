package org.folio.circulation.domain;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

@RunWith(JUnitParamsRunner.class)
public class OverduePeriodCalculatorTest {

  private static final String INTERVAL_MONTHS = "Months";
  private static final String INTERVAL_WEEKS = "Weeks";
  private static final String INTERVAL_DAYS = "Days";
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";

  @Test
  public void shouldCountClosedWithNoGracePeriod() throws ExecutionException, InterruptedException {
    LoanPolicy loanPolicy = createLoanPolicy(null, null);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, true);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(10))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future =
      new OverduePeriodCalculator().calculateOverdueMinutes(loan, systemTime, null);

    int overdueMinutes = future.get().value();
    assertEquals(10, overdueMinutes);
  }

  @Test
  public void shouldCountClosedWithZeroDurationGracePeriod() throws ExecutionException, InterruptedException {
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    LoanPolicy loanPolicy = createLoanPolicy(0, INTERVAL_MINUTES);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, true);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(10))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future =
      new OverduePeriodCalculator().calculateOverdueMinutes(loan, systemTime, null);

    int overdueMinutes = future.get().value();
    assertEquals(10, overdueMinutes);
  }

  @Test
  public void shouldCountClosedWithMissingLoanPolicy() throws ExecutionException, InterruptedException {
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, true);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(10))
      .asDomainObject()
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future =
      new OverduePeriodCalculator().calculateOverdueMinutes(loan, systemTime, null);

    int overdueMinutes = future.get().value();
    assertEquals(10, overdueMinutes);
  }

  @Test
  public void shouldCountClosedAndShouldIgnoreGracePeriod() throws ExecutionException, InterruptedException {
    LoanPolicy loanPolicy = createLoanPolicy(5, INTERVAL_MINUTES);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(true, true);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(10))
      .withDueDateChangedByRecall(true)
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future =
      new OverduePeriodCalculator().calculateOverdueMinutes(loan, systemTime, null);

    int overdueMinutes = future.get().value();
    assertEquals(10, overdueMinutes);
  }

  @Test
  @Parameters({
    "11    | Minutes",
    "70    | Hours",    // 10 + 60
    "1450  | Days",     // 10 + 60 * 24
    "10090 | Weeks",    // 10 + 60 * 24 * 7
    "44650 | Months"    // 10 + 60 * 24 * 31
  })
  public void shouldCountClosedWithGracePeriod(int dueDateOffsetMinutes, String interval)
    throws ExecutionException, InterruptedException {

    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    LoanPolicy loanPolicy = createLoanPolicy(1, interval);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, true);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(dueDateOffsetMinutes))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future =
      new OverduePeriodCalculator().calculateOverdueMinutes(loan, systemTime, null);

    int overdueMinutes = future.get().value();
    assertEquals(10, overdueMinutes);
  }

  private Loan createLoan(DateTime dueDate, boolean dueDateChangedByRecall) {
    return new LoanBuilder()
      .withDueDate(dueDate)
      .withDueDateChangedByRecall(dueDateChangedByRecall)
      .withCheckoutServicePointId(UUID.randomUUID())
      .asDomainObject();

  }

  private LoanPolicy createLoanPolicy(Integer gracePeriodDuration, String gracePeriodInterval) {
    LoanPolicyBuilder builder = new LoanPolicyBuilder();
    if (ObjectUtils.allNotNull(gracePeriodDuration, gracePeriodInterval)) {
      Period gracePeriod = Period.from(gracePeriodDuration, gracePeriodInterval);
      builder = builder.withGracePeriod(gracePeriod);
    }

    return LoanPolicy.from(builder.create());
  }

  private OverdueFinePolicy createOverdueFinePolicy(boolean gracePeriodRecall, boolean countClosed) {
    JsonObject json = new OverdueFinePolicyBuilder()
      .withGracePeriodRecall(gracePeriodRecall)
      .withCountClosed(countClosed)
      .create();

    return OverdueFinePolicy.from(json);
  }
}