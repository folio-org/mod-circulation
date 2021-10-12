package org.folio.circulation.domain;

import static org.folio.circulation.support.utils.DateFormatUtil.parseDateTime;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimePeriodTest {

  @ParameterizedTest
  @CsvSource(value = {
    "2020-07-12T00:00:00.000Z, 2020-07-12T00:10:00.000Z, Minutes, 10",
    "2020-07-12T00:00:00.000Z, 2020-07-12T05:00:00.000Z, Hours, 5",
    "2020-07-12T00:00:00.000Z, 2020-07-18T00:00:00.000Z, Days, 6",
    "2020-07-12T00:00:00.000Z, 2020-07-19T00:00:00.000Z, Weeks, 1",
    "2020-07-12T00:00:00.000Z, 2020-09-12T00:00:00.000Z, Months, 2",
  })
  void canCalculateIntervalBetweenDates(String dateFromString, String dateToString,
    String intervalId, long expectedAmount) {

    final TimePeriod timePeriod = new TimePeriod(1, intervalId);
    final ZonedDateTime dateFrom = parseDateTime(dateFromString);
    final ZonedDateTime dateTo = parseDateTime(dateToString);

    assertThat(timePeriod.between(dateFrom, dateTo), is(expectedAmount));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "2020-07-12T00:05:00.000Z, 2020-07-12T00:00:00.000Z, Minutes, -5",
    "2020-07-12T07:00:00.000Z, 2020-07-12T00:00:00.000Z, Hours, -7",
    "2020-07-17T00:00:00.000Z, 2020-07-09T00:00:00.000Z, Days, -8",
    "2020-07-20T00:00:00.000Z, 2020-07-13T00:00:00.000Z, Weeks, -1",
    "2020-12-12T00:00:00.000Z, 2020-09-12T00:00:00.000Z, Months, -3",
  })
  void canCalculateIntervalIfFromBeforeAfter(String dateFromString, String dateToString,
    String intervalId, long expectedAmount) {

    final TimePeriod timePeriod = new TimePeriod(1, intervalId);
    final ZonedDateTime dateFrom = parseDateTime(dateFromString);
    final ZonedDateTime dateTo = parseDateTime(dateToString);

    assertThat(timePeriod.between(dateFrom, dateTo), is(expectedAmount));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "2020-07-12T00:00:00.000Z, 2020-07-12T00:10:30.000Z, Minutes, 10",
    "2020-07-12T00:00:00.000Z, 2020-07-12T05:30:00.000Z, Hours, 5",
    "2020-07-12T00:00:00.000Z, 2020-07-18T12:00:00.000Z, Days, 6",
    "2020-07-12T00:00:00.000Z, 2020-07-25T00:00:00.000Z, Weeks, 1",
    "2020-07-12T00:00:00.000Z, 2020-10-01T00:00:00.000Z, Months, 2",
  })
  void intervalSetToFloorWhenNumberOfIntervalsIsFraction(
    String dateFromString, String dateToString, String intervalId, long expectedAmount) {

    final TimePeriod timePeriod = new TimePeriod(1, intervalId);
    final ZonedDateTime dateFrom = parseDateTime(dateFromString);
    final ZonedDateTime dateTo = parseDateTime(dateToString);

    assertThat(timePeriod.between(dateFrom, dateTo), is(expectedAmount));
  }
}
