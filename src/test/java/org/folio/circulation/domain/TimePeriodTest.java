package org.folio.circulation.domain;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class TimePeriodTest {

  @Parameters({
    "2020-07-12T00:00:00.000Z, 2020-07-12T00:10:00.000Z, Minutes, 10",
    "2020-07-12T00:00:00.000Z, 2020-07-12T05:00:00.000Z, Hours, 5",
    "2020-07-12T00:00:00.000Z, 2020-07-18T00:00:00.000Z, Days, 6",
    "2020-07-12T00:00:00.000Z, 2020-07-19T00:00:00.000Z, Weeks, 1",
    "2020-07-12T00:00:00.000Z, 2020-09-12T00:00:00.000Z, Months, 2",
  })
  @Test
  public void canCalculateIntervalBetweenDates(String dateFromString, String dateToString,
    String intervalId, long expectedAmount) {

    final TimePeriod timePeriod = new TimePeriod(1, intervalId);
    final DateTime dateFrom = DateTime.parse(dateFromString);
    final DateTime dateTo = DateTime.parse(dateToString);

    assertThat(timePeriod.between(dateFrom, dateTo), is(expectedAmount));
  }

  @Parameters({
    "2020-07-12T00:05:00.000Z, 2020-07-12T00:00:00.000Z, Minutes, -5",
    "2020-07-12T07:00:00.000Z, 2020-07-12T00:00:00.000Z, Hours, -7",
    "2020-07-17T00:00:00.000Z, 2020-07-09T00:00:00.000Z, Days, -8",
    "2020-07-20T00:00:00.000Z, 2020-07-13T00:00:00.000Z, Weeks, -1",
    "2020-12-12T00:00:00.000Z, 2020-09-12T00:00:00.000Z, Months, -3",
  })
  @Test
  public void canCalculateIntervalIfFromBeforeAfter(String dateFromString, String dateToString,
    String intervalId, long expectedAmount) {

    final TimePeriod timePeriod = new TimePeriod(1, intervalId);
    final DateTime dateFrom = DateTime.parse(dateFromString);
    final DateTime dateTo = DateTime.parse(dateToString);

    assertThat(timePeriod.between(dateFrom, dateTo), is(expectedAmount));
  }

  @Parameters({
    "2020-07-12T00:00:00.000Z, 2020-07-12T00:10:30.000Z, Minutes, 10",
    "2020-07-12T00:00:00.000Z, 2020-07-12T05:30:00.000Z, Hours, 5",
    "2020-07-12T00:00:00.000Z, 2020-07-18T12:00:00.000Z, Days, 6",
    "2020-07-12T00:00:00.000Z, 2020-07-25T00:00:00.000Z, Weeks, 1",
    "2020-07-12T00:00:00.000Z, 2020-10-01T00:00:00.000Z, Months, 2",
  })
  @Test
  public void intervalSetToFloorWhenNumberOfIntervalsIsFraction(
    String dateFromString, String dateToString, String intervalId, long expectedAmount) {

    final TimePeriod timePeriod = new TimePeriod(1, intervalId);
    final DateTime dateFrom = DateTime.parse(dateFromString);
    final DateTime dateTo = DateTime.parse(dateToString);

    assertThat(timePeriod.between(dateFrom, dateTo), is(expectedAmount));
  }
}
