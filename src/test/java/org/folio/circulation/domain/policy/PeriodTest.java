package org.folio.circulation.domain.policy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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
  void toMinutes(String interval, Integer duration, int expectedResult) {
    assertEquals(expectedResult, Period.from(duration, interval).toMinutes());
  }

  @Test
  void toMinutesWithNullInterval() {
    Period period = Period.from(10, null);
    assertEquals(0, period.toMinutes());
  }

  @Test
  void toMinutesWithNullDuration() {
    Period period = Period.from(null, "Minutes");
    assertEquals(0, period.toMinutes());
  }

  @Test
  void toMinutesWithUnknownInterval() {
    Period period = Period.from(10, "Unknown interval");
    assertEquals(0, period.toMinutes());
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 5",
    "Hours, 23",
    "Days, 14",
    "Weeks, 3",
    "Months, 10"
  })
  void hasPassedSinceDateTillNowWhenNowAfterTheDate(String interval, int duration) {
    val period = Period.from(duration, interval);
    val startDate = now(UTC).minus(period.timePeriod()).minusSeconds(1);

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
  void hasPassedSinceDateTillNowWhenNowIsTheDate(String interval, int duration) {
    val period = Period.from(duration, interval);
    val startDate = now(UTC).minus(period.timePeriod());

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
  void hasPassedSinceDateTillNowIsFalse(String interval, int duration) {
    val period = Period.from(duration, interval);
    val startDate = now(UTC);

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
  void hasNotPassedSinceDateTillNow(String interval, int duration) {
    val period = Period.from(duration, interval);
    val startDate = now(UTC).plus(period.timePeriod());

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
  void hasNotPassedSinceDateTillNowIsFalseWhenPassed(String interval, int duration) {
    val period = Period.from(duration, interval);
    val startDate = now(UTC).minus(period.timePeriod()).minusSeconds(1);

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
  void isEqualToDateTillNow(String interval, int duration) {
    val period = Period.from(duration, interval);
    val startDate = now(UTC).minus(period.timePeriod());

    assertTrue(period.isEqualToDateTillNow(startDate)
      // Sometimes there is difference in mss
      // additional check to make the test stable
      || period.hasPassedSinceDateTillNow(startDate));
  }

  @ParameterizedTest
  @MethodSource("isValidParameters")
  void isValid(String interval, Integer duration, boolean expectedResult) {
    assertThat(Period.from(duration, interval).isValid(), is(expectedResult));
  }

  private static Object[] isValidParameters() {
    return new Object[] {
      new Object[] { "Minutes", 1 , true },
      new Object[] { "Minutes" , null, false },
      new Object[] { null , 1, false },
      new Object[] { null , null, false }
    };
  }

}
