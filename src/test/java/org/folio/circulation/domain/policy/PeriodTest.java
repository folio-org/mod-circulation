package org.folio.circulation.domain.policy;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

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
    int durationMinutes = 10;
    Period period = Period.from(durationMinutes, "Unknown interval");
    assertEquals(durationMinutes, period.toMinutes());
  }
}