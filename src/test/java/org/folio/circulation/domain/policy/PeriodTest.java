package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import lombok.val;

class PeriodTest {

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 6, 6",
    "Hours, 5, 300",
    "Days, 4, 5760",
    "Weeks, 3, 30240",
    "Months, 2, 89280"
  })
  void toMinutes(String interval, Long duration, int expectedResult) {
    assertEquals(expectedResult, Period.from(duration, interval).toMinutes());
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
    "Minutes, 5",
    "Hours, 23",
    "Days, 14",
    "Weeks, 3",
    "Months, 10"
  })
  void hasPassedSinceDateTillNowWhenNowAfterTheDate(String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = period.minusDate(getZonedDateTime()).minusSeconds(1);

    assertTrue(period.hasPassedSinceDateTillNow(startDate));
    assertFalse(period.hasNotPassedSinceDateTillNow(startDate));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 55",
    "Hours, 32",
    "Days, 65",
    "Weeks, 7",
    "Months, 23"
  })
  void hasPassedSinceDateTillNowWhenNowIsTheDate(String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = period.minusDate(getZonedDateTime());

    assertTrue(period.hasPassedSinceDateTillNow(startDate));
    assertFalse(period.hasNotPassedSinceDateTillNow(startDate));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 33",
    "Hours, 65",
    "Days, 9",
    "Weeks, 12",
    "Months, 3"
  })
  void hasPassedSinceDateTillNowIsFalse(String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = getZonedDateTime();

    assertFalse(period.hasPassedSinceDateTillNow(startDate));
    assertTrue(period.hasNotPassedSinceDateTillNow(startDate));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 12",
    "Hours, 87",
    "Days, 98",
    "Weeks, 23",
    "Months, 4"
  })
  void hasNotPassedSinceDateTillNow(String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = period.plusDate(getZonedDateTime());

    assertTrue(period.hasNotPassedSinceDateTillNow(startDate));
    assertFalse(period.hasPassedSinceDateTillNow(startDate));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 4",
    "Hours, 7",
    "Days, 8",
    "Weeks, 3",
    "Months, 9"
  })
  void hasNotPassedSinceDateTillNowIsFalseWhenPassed(String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = period.minusDate(getZonedDateTime()).minusSeconds(1);

    assertFalse(period.hasNotPassedSinceDateTillNow(startDate));
    assertTrue(period.hasPassedSinceDateTillNow(startDate));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 43",
    "Hours, 65",
    "Days, 87",
    "Weeks, 12",
    "Months, 3"
  })
  void isEqualToDateTillNow(String interval, long duration) {
    val period = Period.from(duration, interval);
    val startDate = period.minusDate(getZonedDateTime());

    assertTrue(period.isEqualToDateTillNow(startDate)
      // Sometimes there is difference in mss
      // additional check to make the test stable
      || period.hasPassedSinceDateTillNow(startDate));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 1, true",
    "Minutes, null, false",
    "null, 1, false",
    "null, null, false"
  }, nullValues = {"null"})
  void isValid(String interval, Long duration, boolean expectedResult) {
    assertThat(Period.from(duration, interval).isValid(), is(expectedResult));
  }

}
