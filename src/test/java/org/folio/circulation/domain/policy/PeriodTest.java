package org.folio.circulation.domain.policy;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(JUnitParamsRunner.class)
public class PeriodTest {

  @Test
  @Parameters
  public void toMinutes(String interval, Integer duration, int expectedResult) {
    assertEquals(expectedResult, Period.from(duration, interval).toMinutes());
  }

  private Object[] parametersForToMinutes() {
    return new Object[]{
      new Object[] {"Minutes", 6, 6},
      new Object[] {"Hours", 5, 300},
      new Object[] {"Days", 4, 5760},
      new Object[] {"Weeks", 3, 30240},
      new Object[] {"Months", 2, 89280},
      new Object[] {"Random", 10, 10},
      new Object[] {null, 10, 0},
      new Object[] {"Minutes", null, 0},
    };
  }
}