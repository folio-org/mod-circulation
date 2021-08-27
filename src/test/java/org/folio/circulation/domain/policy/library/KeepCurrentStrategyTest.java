package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.joda.time.DateTimeConstants.JANUARY;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.DateTimeZone.forOffsetHours;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.IntStream;

import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KeepCurrentStrategyTest {

  @Test
  void testKeepCurrentDateStrategy() {
    ClosedLibraryStrategy keepCurrentStrategy = new KeepCurrentDateStrategy(UTC);
    DateTime requestDate = new DateTime(2019, JANUARY, 1, 0, 0)
      .withZoneRetainFields(UTC);

    Result<DateTime> calculatedDateTime = keepCurrentStrategy.calculateDueDate(requestDate, null);

    DateTime expectedDate = atEndOfDay(requestDate, UTC);
    assertEquals(expectedDate, calculatedDateTime.value());
  }

  @Test
  void testKeepCurrentDateTimeStrategy() {
    ClosedLibraryStrategy keepCurrentStrategy = new KeepCurrentDateTimeStrategy();
    DateTime requestDate = new DateTime(2019, JANUARY, 1, 0, 0)
      .withZoneRetainFields(UTC);

    Result<DateTime> calculatedDateTime = keepCurrentStrategy.calculateDueDate(requestDate, null);

    assertEquals(requestDate, calculatedDateTime.value());
  }

  @Disabled
  @Test
  void shouldAlwaysKeepCurrentDateWhenConvertingToTimeZone() {
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

  @ParameterizedTest
  @CsvSource(value = {
    "16, 16, 5",
    "16, 16, 6",
    "16, 16, 7",
    "16, 16, 8",
    "16, 16, 9",
    "16, 16, 10",
    "16, 16, 11",
    "16, 16, 12",
    "16, 16, 13",
    "16, 16, 14",
    "16, 16, 15",
    "16, 16, 16",
    "16, 16, 17",
    "16, 16, 18",
    "16, 16, 19",
    "16, 16, 20",
    "16, 16, 21",
    "16, 16, 22",
    "16, 16, 23",
    "16, 17, 0",
    "16, 17, 1",
    "16, 17, 2",
    "16, 17, 3",
    "16, 17, 4"
  })
  void shouldNeverChangeLocalDayWhenConvertingToTimeZone(int newYorkDay, int utcDay, int utcHour) {
    final int year = 2020;
    final int month = 11;
    final DateTimeZone newYorkZone = DateTimeZone.forID("America/New_York");
    final KeepCurrentDateStrategy strategy = new KeepCurrentDateStrategy(newYorkZone);

    // https://www.timeanddate.com/worldclock/converter.html?iso=20201117T030000&p1=1440&p2=179
    // https://www.timeanddate.com/worldclock/converter.html?iso=20201116T140000&p1=1440&p2=179
    final DateTime newYorkDateEnd = new DateTime(year, month, newYorkDay, 23, 59, 59, newYorkZone);
    final DateTime requested = new DateTime(year, month, utcDay, utcHour, 0, 0, UTC);
    final DateTime dateEnd = strategy.calculateDueDate(requested, null).value();

    assertEquals(newYorkDateEnd, dateEnd);
  }
}
