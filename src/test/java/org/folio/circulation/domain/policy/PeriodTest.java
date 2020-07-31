package org.folio.circulation.domain.policy;

import static org.folio.circulation.domain.policy.Period.days;
import static org.folio.circulation.domain.policy.Period.months;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.val;

@RunWith(JUnitParamsRunner.class)
public class PeriodTest {

  @Test
  @Parameters({
    "Minutes | 6  | 6",
    "Hours   | 5  | 300",
    "Days    | 4  | 5760",
    "Weeks   | 3  | 30240",
    "Months  | 2  | 89280"
  })
  public void toMinutes(String interval, Integer duration, int expectedResult) {
    assertEquals(expectedResult, Period.from(duration, interval).toMinutes());
  }

  @Test
  public void toMinutesWithNullInterval() {
    Period period = Period.from(10, null);
    assertEquals(0, period.toMinutes());
  }

  @Test
  public void toMinutesWithNullDuration() {
    Period period = Period.from(null, "Minutes");
    assertEquals(0, period.toMinutes());
  }

  @Test
  public void toMinutesWithUnknownInterval() {
    Period period = Period.from(10, "Unknown interval");
    assertEquals(0, period.toMinutes());
  }

  @Test
  public void periodsWithDifferentIntervalNamesIsNotEqual() {
    val oneMonth = months(1);
    val thirtyOneDay = days(31);

    assertNotEquals(oneMonth, thirtyOneDay);
  }

  @Test
  public void twoPeriodsAreEqual() {
    val oneMonth = months(1);
    val oneMonthCopy = months(1);

    assertEquals(oneMonth, oneMonthCopy);
    assertThat(oneMonth.compareTo(oneMonthCopy), is(0));
  }

  @Test
  public void canComparePeriodsWithDifferentIntervals() {
    val oneMonth = months(1);
    val thirtyTwoDays = days(32);

    assertThat(oneMonth.compareTo(thirtyTwoDays), equalTo(-1));
    assertThat(thirtyTwoDays.compareTo(oneMonth), equalTo(1));
  }
}
