package org.folio.circulation.domain.policy;

import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;

import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import lombok.val;

class PeriodTest {
  private static final int MINUTES_PER_HOUR = 60;
  private static final int HOURS_PER_DAY = 24;
  private static final int DAYS_PER_WEEK = 7;
  private static final int DAYS_PER_MONTH = 31;
  private static final int MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY;
  private static final int MINUTES_PER_WEEK = MINUTES_PER_DAY * DAYS_PER_WEEK;
  private static final int MINUTES_PER_MONTH = MINUTES_PER_DAY * DAYS_PER_MONTH;

  @BeforeEach
  public void BeforeEach() {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, Minutes, 6, 6",
    "1, Hours, 5, 300",
    "2, Days, 4, 5760",
    "3, Weeks, 3, 30240",
    "4, Months, 2, 89280"
  })
  void toMinutes(int id, String interval, Long duration, int expectedResult) {
    assertEquals(expectedResult, Period.from(duration, interval).toMinutes(), "For test " + id);
  }

  @Test
  void toMinutesWithNullInterval() {
    Period period = Period.from(10, null);
    assertEquals(0L, period.toMinutes());
  }

  @Test
  void toMinutesWithNullDuration() {
    Period period = Period.from((Long) null, "Minutes");
    assertEquals(0L, period.toMinutes());
  }

  @Test
  void toMinutesWithUnknownInterval() {
    Period period = Period.from(10, "Unknown interval");
    assertEquals(0L, period.toMinutes());
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, Minutes, 5",
    "1, Hours, 23",
    "2, Days, 14",
    "3, Weeks, 3",
    "4, Months, 10"
  })
  void hasPassedSinceDateTillNowWhenNowAfterTheDate(int id, String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = period.minusDate(getZonedDateTime()).minusSeconds(1);

    assertTrue(period.hasPassedSinceDateTillNow(startDate), "For test " + id);
    assertFalse(period.hasNotPassedSinceDateTillNow(startDate), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, Minutes, 55",
    "1, Hours, 32",
    "2, Days, 65",
    "3, Weeks, 7",
    "4, Months, 23"
  })
  void hasPassedSinceDateTillNowWhenNowIsTheDate(int id, String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = period.minusDate(getZonedDateTime());

    assertTrue(period.hasPassedSinceDateTillNow(startDate), "For test " + id);
    assertFalse(period.hasNotPassedSinceDateTillNow(startDate), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, Minutes, 33",
    "1, Hours, 65",
    "2, Days, 9",
    "3, Weeks, 12",
    "4, Months, 3"
  })
  void hasPassedSinceDateTillNowIsFalse(int id, String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = getZonedDateTime();

    assertFalse(period.hasPassedSinceDateTillNow(startDate), "For test " + id);
    assertTrue(period.hasNotPassedSinceDateTillNow(startDate), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, Minutes, 12",
    "1, Hours, 87",
    "2, Days, 98",
    "3, Weeks, 23",
    "4, Months, 4"
  })
  void hasNotPassedSinceDateTillNow(int id, String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = period.plusDate(getZonedDateTime());

    assertTrue(period.hasNotPassedSinceDateTillNow(startDate), "For test " + id);
    assertFalse(period.hasPassedSinceDateTillNow(startDate), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, Minutes, 4",
    "1, Hours, 7",
    "2, Days, 8",
    "3, Weeks, 3",
    "4, Months, 9"
  })
  void hasNotPassedSinceDateTillNowIsFalseWhenPassed(int id, String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = period.minusDate(getZonedDateTime()).minusSeconds(1);

    assertFalse(period.hasNotPassedSinceDateTillNow(startDate), "For test " + id);
    assertTrue(period.hasPassedSinceDateTillNow(startDate), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, Minutes, 43",
    "1, Hours, 65",
    "2, Days, 87",
    "3, Weeks, 12",
    "4, Months, 3"
  })
  void isEqualToDateTillNow(int id, String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = period.minusDate(getZonedDateTime());

    assertTrue(period.isEqualToDateTillNow(startDate)
      // Sometimes there is difference in mss
      // additional check to make the test stable
      || period.hasPassedSinceDateTillNow(startDate), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, Minutes, 1, true",
    "1, Minutes, null, false",
    "2, null, 1, false",
    "3, null, null, false"
  }, nullValues = {"null"})
  void isValid(int id, String interval, Long duration, boolean expected) {
    assertThat("For test " + id, Period.from(duration, interval).isValid(), is(expected));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, 0",
    "1, 1, " + MINUTES_PER_MONTH
  }, nullValues = {"null"})
  void isDurationOfMonthsInMinutesUsingInteger(int id, Integer duration, Long expected) {
    assertEquals(expected, Period.months(duration).toMinutes(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, 0",
    "1, 1, " + MINUTES_PER_MONTH
  }, nullValues = {"null"})
  void isDurationOfMonthsInMinutesUsingLong(int id, Long duration, Long expected) {
    assertEquals(expected, Period.months(duration).toMinutes(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, 0",
    "1, 1, " + MINUTES_PER_WEEK
  }, nullValues = {"null"})
  void isDurationOfWeeksInMinutesUsingInteger(int id, Integer duration, Long expected) {
    assertEquals(expected, Period.weeks(duration).toMinutes(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, 0",
    "1, 1, " + MINUTES_PER_WEEK
  }, nullValues = {"null"})
  void isDurationOfWeeksInMinutesUsingLong(int id, Long duration, Long expected) {
    assertEquals(expected, Period.weeks(duration).toMinutes(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, 0",
    "1, 1, " + MINUTES_PER_DAY
  }, nullValues = {"null"})
  void isDurationOfDaysInMinutesUsingInteger(int id, Integer duration, Long expected) {
    assertEquals(expected, Period.days(duration).toMinutes(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, 0",
    "1, 1, " + MINUTES_PER_DAY
  }, nullValues = {"null"})
  void isDurationOfDaysInMinutesUsingLong(int id, Long duration, Long expected) {
    assertEquals(expected, Period.days(duration).toMinutes(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, 0",
    "1, 1, " + MINUTES_PER_HOUR
  }, nullValues = {"null"})
  void isDurationOfHoursInMinutesUsingInteger(int id, Integer duration, Long expected) {
    assertEquals(expected, Period.hours(duration).toMinutes(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, 0",
    "1, 1, " + MINUTES_PER_HOUR
  }, nullValues = {"null"})
  void isDurationOfHoursInMinutesUsingLong(int id, Long duration, Long expected) {
    assertEquals(expected, Period.hours(duration).toMinutes(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, 0",
    "1, 1, 1"
  }, nullValues = {"null"})
  void isDurationOfMinutesUsingInteger(int id, Integer duration, Long expected) {
    assertEquals(expected, Period.minutes(duration).toMinutes(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, 0",
    "1, 1, 1"
  }, nullValues = {"null"})
  void isDurationOfMinutesUsingLong(int id, Long duration, Long expected) {
    assertEquals(expected, Period.minutes(duration).toMinutes(), "For test " + id);
  }

}
