package org.folio.circulation.domain.policy.library;

import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.stream.IntStream;

import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KeepCurrentStrategyTest {

  @Test
  void testKeepCurrentDateStrategy() {
    ClosedLibraryStrategy keepCurrentStrategy = new KeepCurrentDateStrategy(UTC);
    ZonedDateTime requestDate = ZonedDateTime.of(2019, 1, 1, 0, 0, 0, 0, UTC);

    Result<ZonedDateTime> calculatedDateTime = keepCurrentStrategy.calculateDueDate(requestDate, null);

    ZonedDateTime expectedDate = atEndOfDay(requestDate, UTC);
    assertEquals(expectedDate, calculatedDateTime.value());
  }

  @Test
  void testKeepCurrentDateTimeStrategy() {
    ClosedLibraryStrategy keepCurrentStrategy = new KeepCurrentDateTimeStrategy();
    ZonedDateTime requestDate = ZonedDateTime.of(2019, 1, 1, 0, 0, 0, 0, UTC);

    Result<ZonedDateTime> calculatedDateTime = keepCurrentStrategy.calculateDueDate(requestDate, null);

    assertEquals(requestDate, calculatedDateTime.value());
  }

  @Disabled
  @Test
  void shouldAlwaysKeepCurrentDateWhenConvertingToTimeZone() {
    final int year = 2020;
    final int month = 11;
    final int dayOfMonth = 17;

    final ZonedDateTime now = ZonedDateTime.of(year, month, dayOfMonth, 9, 47, 0, 0, UTC);

    IntStream.rangeClosed(-12, 12)
      .forEach(offsetHours -> {
        final ZoneOffset zoneOffset = ZoneOffset.ofHours(offsetHours);
        final KeepCurrentDateStrategy strategy = new KeepCurrentDateStrategy(zoneOffset);
        final ZonedDateTime newDueDate = strategy.calculateDueDate(now, null).value();

        assertEquals(year, newDueDate.getYear());
        assertEquals(month, newDueDate.getMonthValue());
        assertEquals(dayOfMonth, newDueDate.getDayOfMonth());

        assertEquals(23, newDueDate.getHour());
        assertEquals(59, newDueDate.getMinute());
        assertEquals(59, newDueDate.getSecond());

        final int zoneOffsetInSeconds = offsetHours * 60 * 60;
        assertEquals(zoneOffsetInSeconds, zoneOffset.getTotalSeconds());
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
  void shouldAlwaysStayTheSameZonedDay(int newYorkDay, int utcDay, int utcHour) {
    final int year = 2020;
    final int month = 11;
    final ZoneId newYorkZone = ZoneId.of("America/New_York");
    final KeepCurrentDateStrategy strategy = new KeepCurrentDateStrategy(newYorkZone);

    // https://www.timeanddate.com/worldclock/converter.html?iso=20201117T030000&p1=1440&p2=179
    // https://www.timeanddate.com/worldclock/converter.html?iso=20201116T140000&p1=1440&p2=179
    final ZonedDateTime newYorkDateEnd = ZonedDateTime.of(year, month, newYorkDay, 23, 59, 59, 0, newYorkZone);
    final ZonedDateTime requested = ZonedDateTime.of(year, month, utcDay, utcHour, 0, 0, 0, UTC);
    final ZonedDateTime dateEnd = strategy.calculateDueDate(requested, null).value();

    assertEquals(newYorkDateEnd, dateEnd);
  }
}
