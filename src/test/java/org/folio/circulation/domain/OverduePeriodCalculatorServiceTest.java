package org.folio.circulation.domain;

import static api.support.fixtures.OpeningHourExamples.afternoon;
import static api.support.fixtures.OpeningHourExamples.allDay;
import static api.support.fixtures.OpeningHourExamples.morning;
import static java.util.Collections.singletonList;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import io.vertx.core.json.JsonObject;

class OverduePeriodCalculatorServiceTest {
  private static final OverduePeriodCalculatorService calculator =
    new OverduePeriodCalculatorService(null, null);
  private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
  private static final ZoneId LONDON = ZoneId.of("Europe/London");
  private static final ZoneId UTC = ZoneId.of("UTC");

  @Test
  void preconditionsCheckLoanHasNoDueDate() {
    ZonedDateTime systemTime = ClockUtil.getZonedDateTime();
    Loan loan = new LoanBuilder().asDomainObject();

    assertFalse(calculator.preconditionsAreMet(loan, systemTime, true));
  }

  @Test
  void preconditionsCheckLoanDueDateIsInFuture() {
    ZonedDateTime systemTime = ClockUtil.getZonedDateTime();
    Loan loan = new LoanBuilder()
      .withDueDate(systemTime.plusDays(1))
      .asDomainObject();

    assertFalse(calculator.preconditionsAreMet(loan, systemTime, true));
  }

  @Test
  void preconditionsCheckCountClosedIsNull() {
    ZonedDateTime systemTime = ClockUtil.getZonedDateTime();
    Loan loan = new LoanBuilder()
      .withDueDate(systemTime.minusDays(1))
      .asDomainObject();

    assertFalse(calculator.preconditionsAreMet(loan, systemTime, null));
  }

  @Test
  void allPreconditionsAreMet() {
    ZonedDateTime systemTime = ClockUtil.getZonedDateTime();
    Loan loan = new LoanBuilder()
      .withDueDate(systemTime.minusDays(1))
      .asDomainObject();

    assertTrue(calculator.preconditionsAreMet(loan, systemTime, true));
  }

  @Test
  void countOverdueMinutesWithClosedDays() throws ExecutionException, InterruptedException {
    final int expectedResult = 60 * 24 * 7 + 1;
    final ZonedDateTime systemTime = getZonedDateTime();

    Loan loan = new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(expectedResult))
      .asDomainObject();

    int actualResult = calculator.getOverdueMinutes(loan, systemTime, true, UTC).get().value();

    assertEquals(expectedResult, actualResult);
  }

  @ParameterizedTest
  @MethodSource("getOpeningDayDurationTestParameters")
  void getOpeningDayDurationTest(List<OpeningDay> openingDays, int expectedResult) {

    ZonedDateTime dueDate = ZonedDateTime.parse("2020-04-08T14:00:00.000Z");
    ZonedDateTime returnDate = ZonedDateTime.parse("2020-04-10T15:00:00.000Z");

    int actualResult = calculator.getOpeningDaysDurationMinutes(
      openingDays, dueDate, returnDate).value();
    assertEquals(expectedResult, actualResult);
  }

  private static Object[] getOpeningDayDurationTestParameters() {
    List<OpeningDay> zeroDays = Collections.emptyList();

    List<OpeningDay> regular = Arrays.asList(
      createOpeningDay(false, LocalDate.parse("2020-04-08"), UTC),
      createOpeningDay(false, LocalDate.parse("2020-04-09"), UTC),
      createOpeningDay(false, LocalDate.parse("2020-04-10"), UTC));

    List<OpeningDay> allDay = Arrays.asList(
      createOpeningDay(true, LocalDate.parse("2020-04-08"), NEW_YORK),
      createOpeningDay(true, LocalDate.parse("2020-04-09"), NEW_YORK),
      createOpeningDay(true, LocalDate.parse("2020-04-10"), NEW_YORK));

    List<OpeningDay> mixed = Arrays.asList(
      createOpeningDay(false, LocalDate.parse("2020-04-08"), LONDON),
      createOpeningDay(true, LocalDate.parse("2020-04-09"), LONDON),
      createOpeningDay(false, LocalDate.parse("2020-04-10"), LONDON));

    List<OpeningDay> invalid = Arrays.asList(
      new OpeningDay(
        singletonList(new OpeningHour(null, null)), LocalDate.parse("2020-04-08"), false, true, UTC
      ),
      new OpeningDay(  // startTime after endTime
        singletonList(new OpeningHour(LocalTime.of(6, 0), LocalTime.of(5, 0))),
        LocalDate.parse("2020-04-09"), false, true, UTC)
    );

    List<OpeningDay> allDaysClosed = Arrays.asList(
      new OpeningDay(singletonList(allDay()), LocalDate.parse("2020-04-08"), true, false, NEW_YORK),
      new OpeningDay(singletonList(allDay()), LocalDate.parse("2020-04-09"), true, false, NEW_YORK),
      new OpeningDay(singletonList(allDay()), LocalDate.parse("2020-04-10"), true, false, NEW_YORK)
    );

    List<OpeningDay> secondDayClosed = Arrays.asList(
      new OpeningDay(singletonList(allDay()), LocalDate.parse("2020-04-08"), true, true, NEW_YORK),
      new OpeningDay(singletonList(allDay()), LocalDate.parse("2020-04-09"), true, false, NEW_YORK),
      new OpeningDay(singletonList(allDay()), LocalDate.parse("2020-04-10"), true, true, NEW_YORK)
    );

    return new Object[] {
      new Object[]{zeroDays, 0},
      new Object[]{regular, 60 * 21},
      new Object[]{allDay, 60 * 49 - 2},
      new Object[]{mixed, 60 * 35 - 1},
      new Object[]{invalid, 0},
      new Object[]{allDaysClosed, 0},
      new Object[]{secondDayClosed, 60 * 25 - 1}
    };
  }

  @ParameterizedTest
  @MethodSource("gracePeriodAdjustmentTestParameters")
  void gracePeriodAdjustmentTest(
    int overdueMinutes,
    String gracePeriodInterval,
    int gracePeriodDuration,
    Boolean ignoreGracePeriodForRecalls,
    boolean dueDateChangedByRecall,
    int expectedResult) {

    final Loan loan = new LoanBuilder()
      .withDueDateChangedByRecall(dueDateChangedByRecall)
      .asDomainObject()
      .withLoanPolicy(createLoanPolicy(gracePeriodDuration, gracePeriodInterval))
      .withOverdueFinePolicy(createOverdueFinePolicy(ignoreGracePeriodForRecalls, null));

    int actualResult = calculator.adjustOverdueWithGracePeriod(loan, overdueMinutes).value();
    assertEquals(expectedResult, actualResult);
  }

  private static Object[] gracePeriodAdjustmentTestParameters() {
    Stream<Object> parametersStream = Arrays.stream(new GracePeriodParams[] {
      new GracePeriodParams(10, "Minutes", 5, 12),
      new GracePeriodParams(255, "Hours", 4, 5),
      new GracePeriodParams(4350, "Days", 3, 4),
      new GracePeriodParams(20200, "Weeks", 2, 3),
      new GracePeriodParams(44700, "Months", 1, 2)
    }).map(p -> new Object[]{
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, null, false,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, null, true,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, false, false,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, true, false,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, false, true,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, true, true,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, null, false, 0},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, null, true, 0},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, false, false, 0},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, true, false, 0},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, false, true, 0},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, true, true,
        p.overdueMinutes}
    })
      .flatMap(Arrays::stream);

    return Stream.concat(parametersStream,
      Stream.of(new Object[][] {{11, "Unknown interval", 9, false, false, 11}})).toArray();
  }

  private static LoanPolicy createLoanPolicy(Integer gracePeriodDuration, String gracePeriodInterval) {
    LoanPolicyBuilder builder = new LoanPolicyBuilder();
    if (ObjectUtils.allNotNull(gracePeriodDuration, gracePeriodInterval)) {
      Period gracePeriod = Period.from(gracePeriodDuration, gracePeriodInterval);
      builder = builder.withGracePeriod(gracePeriod);
    }
    return LoanPolicy.from(builder.create());
  }

  private static OverdueFinePolicy createOverdueFinePolicy(Boolean gracePeriodRecall, Boolean countClosed) {
    JsonObject overdueFineObject = new JsonObject();
    overdueFineObject.put("quantity", 1);
    overdueFineObject.put("intervalId", "minute");

    JsonObject overdueRecallFineObject = new JsonObject();
    overdueRecallFineObject.put("quantity", 1);
    overdueRecallFineObject.put("intervalId", "minute");

    JsonObject json = new OverdueFinePolicyBuilder()
      .withGracePeriodRecall(gracePeriodRecall)
      .withCountClosed(countClosed)
      .withOverdueFine(overdueFineObject)
      .withOverdueRecallFine(overdueRecallFineObject)
      .withMaxOverdueFine(5)
      .withMaxOverdueRecallFine(5)
      .create();

    return OverdueFinePolicy.from(json);
  }

  private static OpeningDay createOpeningDay(
    boolean allDay, LocalDate date, ZoneId dateTimeZone) {

    return new OpeningDay(
      allDay ? singletonList(allDay()) : Arrays.asList(morning(), afternoon()),
      date, allDay, true, dateTimeZone
      );
  }

  private static final class GracePeriodParams {
    private final int overdueMinutes;
    private final String interval;
    private final int gracePeriodLessThanOverdue;
    private final int gracePeriodGreaterThanOverdue;

    public GracePeriodParams(int overdueMinutes, String interval, int gracePeriodLessThanOverdue,
      int gracePeriodGreaterThanOverdue) {

      this.overdueMinutes = overdueMinutes;
      this.interval = interval;
      this.gracePeriodLessThanOverdue = gracePeriodLessThanOverdue;
      this.gracePeriodGreaterThanOverdue = gracePeriodGreaterThanOverdue;
    }
  }
}
