package org.folio.circulation.domain.policy.library;

import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.DateTimeZone.forOffsetHours;
import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

public class KeepCurrentStrategyTest {

  @Test
  public void testKeepCurrentDateStrategy() {
    ClosedLibraryStrategy keepCurrentStrategy = new KeepCurrentDateStrategy(UTC);
    DateTime requestDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 1, 0, 0)
        .withZoneRetainFields(UTC);
    Result<DateTime> calculatedDateTime = keepCurrentStrategy.calculateDueDate(requestDate, null);

    DateTime expectedDate = requestDate.withTime(END_OF_A_DAY);
    assertEquals(expectedDate, calculatedDateTime.value());
  }

  @Test
  public void testKeepCurrentDateTimeStrategy() {
    ClosedLibraryStrategy keepCurrentStrategy = new KeepCurrentDateTimeStrategy();
    DateTime requestDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 1, 0, 0)
        .withZoneRetainFields(UTC);
    Result<DateTime> calculatedDateTime = keepCurrentStrategy.calculateDueDate(requestDate, null);

    assertEquals(requestDate, calculatedDateTime.value());
  }

  @Test
  public void shouldAlwaysKeepCurrentDateWhenConvertingToTimeZone() {
    final int year = 2020;
    final int month = 11;
    final int dayOfMonth = 17;

    final DateTime now = new DateTime(year, month, dayOfMonth, 9, 47, UTC);

    IntStream.rangeClosed(-12, 12)
      .forEach(zoneOffset -> {
        final DateTimeZone timeZone = forOffsetHours(zoneOffset);
        final KeepCurrentDateStrategy strategy = new KeepCurrentDateStrategy(timeZone);

        final DateTime newDueDate = strategy.calculateDueDate(now, null).value();

        assertEquals(year, newDueDate.getYear());
        assertEquals(month, newDueDate.getMonthOfYear());
        assertEquals(dayOfMonth, newDueDate.getDayOfMonth());

        assertEquals(23, newDueDate.getHourOfDay());
        assertEquals(59, newDueDate.getMinuteOfHour());
        assertEquals(59, newDueDate.getSecondOfMinute());

        final int zoneOffsetInMs = zoneOffset * 60 * 60 * 1000;
        assertEquals(zoneOffsetInMs, newDueDate.getZone().getOffset(now));
      });
  }
}
