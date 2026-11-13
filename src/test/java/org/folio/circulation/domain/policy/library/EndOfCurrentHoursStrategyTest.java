package org.folio.circulation.domain.policy.library;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.support.Interval;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

class EndOfCurrentHoursStrategyTest {

  private static final LocalDate THURSDAY = LocalDate.of(2019, Month.JANUARY, 31);
  private static final LocalDate FRIDAY = LocalDate.of(2019, Month.FEBRUARY, 1);
  private static final LocalDate SATURDAY = LocalDate.of(2019, Month.FEBRUARY, 2);
  private static final LocalDate MONDAY = LocalDate.of(2019, Month.FEBRUARY, 4);

  private static final List<OpeningHour> WORKING_HOURS = Arrays.asList(
    new OpeningHour(LocalTime.of(8, 0), LocalTime.of(12, 0)),
    new OpeningHour(LocalTime.of(14, 0), LocalTime.of(19, 0)));

  /**
   * Calendar surrounding a projected due date that falls on Saturday:
   * the last open day before it (FRI), the closed day itself (SAT), the next open day (MON).
   * THU is intentionally not part of the calendar.
   */
  private static final AdjacentOpeningDays FRI_SAT_MON = new AdjacentOpeningDays(
    new OpeningDay(WORKING_HOURS, FRIDAY, false, true),
    new OpeningDay(Collections.emptyList(), SATURDAY, false, false),
    new OpeningDay(WORKING_HOURS, MONDAY, false, true));

  @Test
  void dueDateIsEndOfLastOpenHoursBeforeProjectedDueDateWhenCheckoutIsBeforeSurroundingCalendar() {
    // 48 hours loan checked out on Thursday morning, projected due date is Saturday
    ZonedDateTime checkout = ZonedDateTime.of(THURSDAY, LocalTime.of(11, 0), UTC);
    ZonedDateTime projectedDueDate = checkout.plusHours(48);

    Result<ZonedDateTime> result = new EndOfCurrentHoursStrategy(checkout, UTC)
      .calculateDueDate(projectedDueDate, FRI_SAT_MON);

    assertTrue(result.succeeded(), "due date calculation should not fail");
    assertEquals(ZonedDateTime.of(FRIDAY, LocalTime.of(19, 0), UTC), result.value());
  }

  @Test
  void dueDateIsEndOfCurrentOpenHoursWhenCheckoutIsWithinSurroundingCalendar() {
    // 24 hours loan checked out on Friday morning, projected due date is Saturday
    ZonedDateTime checkout = ZonedDateTime.of(FRIDAY, LocalTime.of(9, 0), UTC);
    ZonedDateTime projectedDueDate = checkout.plusHours(24);

    Result<ZonedDateTime> result = new EndOfCurrentHoursStrategy(checkout, UTC)
      .calculateDueDate(projectedDueDate, FRI_SAT_MON);

    assertTrue(result.succeeded(), "due date calculation should not fail");
    assertEquals(ZonedDateTime.of(FRIDAY, LocalTime.of(12, 0), UTC), result.value());
  }

  @Test
  void dueDateIsEndOfLastOpenHoursForLoanSpanningSeveralClosedDays() {
    // 72 hours loan checked out on Thursday, projected due date is Sunday (still closed)
    ZonedDateTime checkout = ZonedDateTime.of(THURSDAY, LocalTime.of(11, 0), UTC);
    ZonedDateTime projectedDueDate = checkout.plusHours(72);

    Result<ZonedDateTime> result = new EndOfCurrentHoursStrategy(checkout, UTC)
      .calculateDueDate(projectedDueDate, FRI_SAT_MON);

    assertTrue(result.succeeded(), "due date calculation should not fail");
    assertEquals(ZonedDateTime.of(FRIDAY, LocalTime.of(19, 0), UTC), result.value());
  }

  @Test
  void dueDateCalculationFailsWhenCheckoutIsAfterTheWholeTimetable() {
    // checkout time is after every interval of the timetable, so there is nothing to move to
    ZonedDateTime checkout = ZonedDateTime.of(MONDAY.plusDays(1), LocalTime.of(11, 0), UTC);
    ZonedDateTime projectedDueDate = ZonedDateTime.of(SATURDAY, LocalTime.of(11, 0), UTC);

    Result<ZonedDateTime> result = new EndOfCurrentHoursStrategy(checkout, UTC)
      .calculateDueDate(projectedDueDate, FRI_SAT_MON);

    assertTrue(result.failed(), "due date calculation should fail when checkout is after " +
      "the end of the last open hours before the projected due date");
  }

  @Test
  void dueDateCalculationFailsWhenRequestedIntervalHasNoPreviousInterval() {
    ZonedDateTime checkout = ZonedDateTime.of(THURSDAY, LocalTime.of(11, 0), UTC);
    LibraryInterval requestedInterval = new LibraryInterval(new Interval(
      ZonedDateTime.of(SATURDAY, LocalTime.MIDNIGHT, UTC),
      ZonedDateTime.of(MONDAY, LocalTime.MIDNIGHT, UTC)), false);
    LibraryTimetable timetable = new LibraryTimetable(requestedInterval);

    Result<ZonedDateTime> result = new EndOfCurrentHoursStrategy(checkout, UTC)
      .calculateIfClosed(timetable, requestedInterval);

    assertTrue(result.failed(), "due date calculation should fail without a previous interval");
  }

  @Test
  void dueDateCalculationFailsWhenPreviousIntervalIsClosed() {
    ZonedDateTime checkout = ZonedDateTime.of(THURSDAY, LocalTime.of(11, 0), UTC);
    LibraryInterval previousInterval = new LibraryInterval(new Interval(
      ZonedDateTime.of(FRIDAY, LocalTime.MIDNIGHT, UTC),
      ZonedDateTime.of(SATURDAY, LocalTime.MIDNIGHT, UTC)), false);
    LibraryInterval requestedInterval = new LibraryInterval(new Interval(
      ZonedDateTime.of(SATURDAY, LocalTime.MIDNIGHT, UTC),
      ZonedDateTime.of(MONDAY, LocalTime.MIDNIGHT, UTC)), false);
    requestedInterval.setPrevious(previousInterval);
    LibraryTimetable timetable = new LibraryTimetable(requestedInterval);

    Result<ZonedDateTime> result = new EndOfCurrentHoursStrategy(checkout, UTC)
      .calculateIfClosed(timetable, requestedInterval);

    assertTrue(result.failed(), "due date calculation should fail when previous interval is closed");
  }

  @Test
  void dueDateCalculationFailsWhenThereIsNoOpenHoursBeforeProjectedDueDate() {
    AdjacentOpeningDays noPreviousOpenDay = new AdjacentOpeningDays(
      OpeningDay.createClosedDay(),
      new OpeningDay(Collections.emptyList(), SATURDAY, false, false),
      new OpeningDay(WORKING_HOURS, MONDAY, false, true));

    ZonedDateTime checkout = ZonedDateTime.of(THURSDAY, LocalTime.of(11, 0), UTC);

    Result<ZonedDateTime> result = new EndOfCurrentHoursStrategy(checkout, UTC)
      .calculateDueDate(checkout.plusHours(48), noPreviousOpenDay);

    assertTrue(result.failed(), "due date calculation should fail without open hours to move to");
  }
}
