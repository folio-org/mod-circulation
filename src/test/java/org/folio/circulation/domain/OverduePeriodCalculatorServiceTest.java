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
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static api.support.fixtures.OpeningHourExamples.afternoon;
import static api.support.fixtures.OpeningHourExamples.allDay;
import static api.support.fixtures.OpeningHourExamples.morning;
import static org.folio.circulation.domain.OpeningDay.createOpeningDay;
import static org.joda.time.DateTimeConstants.MINUTES_PER_DAY;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;
import static org.joda.time.DateTimeConstants.MINUTES_PER_WEEK;
import static org.junit.Assert.*;

@RunWith(JUnitParamsRunner.class)
public class OverduePeriodCalculatorServiceTest {
  private static final OverduePeriodCalculatorService calculator =
    new OverduePeriodCalculatorService(null);

  @Test
  @Parameters
  public void preconditionsTest(DateTime systemTime, Loan loan) {
    assertTrue(calculator.preconditionsAreNotMet(loan, systemTime));
  }

  private Object[] parametersForPreconditionsTest() {
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);

    Loan noDueDate = new LoanBuilder().asDomainObject();

    Loan dueDateInFuture = new LoanBuilder()
      .withDueDate(systemTime.plusDays(1))
      .asDomainObject();

    Loan nullCountClosed = new LoanBuilder()
      .withDueDate(systemTime.minusDays(1))
      .asDomainObject()
      .withOverdueFinePolicy(createOverdueFinePolicy(null, null));

    return new Object[] {
      new Object[] {systemTime, noDueDate},
      new Object[] {systemTime, dueDateInFuture},
      new Object[] {systemTime, nullCountClosed}
    };
  }

  @Test
  public void countOverdueMinutesWithClosedDays() throws ExecutionException, InterruptedException {
    int expectedResult = MINUTES_PER_WEEK + MINUTES_PER_DAY + MINUTES_PER_HOUR + 1;
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);

    Loan loan = new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(expectedResult))
      .asDomainObject()
      .withOverdueFinePolicy(createOverdueFinePolicy(null, true));

    int actualResult = calculator.getOverdueMinutes(loan, systemTime).get().value();

    assertEquals(expectedResult, actualResult);
  }

  @Test
  @Parameters
  public void getOpeningPeriodDurationTest(List<OpeningPeriod> openingPeriods, int expectedResult) {
    int actualResult = calculator.getOpeningPeriodsDurationMinutes(openingPeriods).value();
    assertEquals(expectedResult, actualResult);
  }

  private Object[] parametersForGetOpeningPeriodDurationTest() {
    List<OpeningPeriod> zeroPeriods = Collections.emptyList();

    LocalDate today = LocalDate.now(DateTimeZone.UTC);
    LocalDate yesterday = today.minusDays(1);
    LocalDate tomorrow = today.plusDays(1);

    List<OpeningPeriod> threeDaysRegular = Arrays.asList(
      createOpeningPeriod(yesterday, false),
      createOpeningPeriod(today, false),
      createOpeningPeriod(tomorrow, false));

    List<OpeningPeriod> threeDaysAllDay = Arrays.asList(
      createOpeningPeriod(yesterday, true),
      createOpeningPeriod(today, true),
      createOpeningPeriod(tomorrow, true));

    List<OpeningPeriod> threeDaysMixed = Arrays.asList(
      createOpeningPeriod(yesterday, false),
      createOpeningPeriod(today, true),
      createOpeningPeriod(tomorrow, false));

    return new Object[]{
      new Object[]{zeroPeriods, 0},
      new Object[]{threeDaysRegular, MINUTES_PER_HOUR * 10 * 3},
      new Object[]{threeDaysAllDay, MINUTES_PER_DAY * 3},
      new Object[]{threeDaysMixed, MINUTES_PER_DAY + MINUTES_PER_HOUR * 10 * 2}
    };
  }

  @Test
  @Parameters({
    "11    | Minutes | 5 | null  | false | 6",
    "255   | Hours   | 4 | null  | true  | 15",
    "4350  | Days    | 3 | false | false | 30",
    "20200 | Weeks   | 2 | false | true  | 40",
    "44700 | Months  | 1 | true  | false | 60",
    "5     | Minutes | 9 | false | false | 0",
    "9     | Minutes | 9 | false | false | 0",
    "11    | Random  | 9 | false | false | 2", // invalid intervals are treated as "Minutes"
    "11    | Minutes | 9 | true  | true  | 11" // grace period is ignored
  })
  public void gracePeriodAdjustmentTest(
    int overdueMinutes,
    String gracePeriodInterval,
    int gracePeriodDuration,
    Boolean gracePeriodRecall,
    boolean dueDateChangedByRecall,
    int expectedResult) {

    final Loan loan = new LoanBuilder()
      .withDueDateChangedByRecall(dueDateChangedByRecall)
      .asDomainObject()
      .withLoanPolicy(createLoanPolicy(gracePeriodDuration, gracePeriodInterval))
      .withOverdueFinePolicy(createOverdueFinePolicy(gracePeriodRecall, null));

    int actualResult = calculator.adjustOverdueWithGracePeriod(loan, overdueMinutes).value();
    assertEquals(expectedResult, actualResult);
  }

  private LoanPolicy createLoanPolicy(Integer gracePeriodDuration, String gracePeriodInterval) {
    LoanPolicyBuilder builder = new LoanPolicyBuilder();
    if (ObjectUtils.allNotNull(gracePeriodDuration, gracePeriodInterval)) {
      Period gracePeriod = Period.from(gracePeriodDuration, gracePeriodInterval);
      builder = builder.withGracePeriod(gracePeriod);
    }
    return LoanPolicy.from(builder.create());
  }

  private OverdueFinePolicy createOverdueFinePolicy(Boolean gracePeriodRecall, Boolean countClosed) {
    JsonObject json = new OverdueFinePolicyBuilder()
      .withGracePeriodRecall(gracePeriodRecall)
      .withCountClosed(countClosed)
      .create();

    return OverdueFinePolicy.from(json);
  }

  private OpeningPeriod createOpeningPeriod(LocalDate date, boolean allDay) {
    return new OpeningPeriod(date,
      createOpeningDay(
        allDay ? Collections.singletonList(allDay()) : Arrays.asList(morning(), afternoon()),
        null, allDay, true
      )
    );
  }

}
