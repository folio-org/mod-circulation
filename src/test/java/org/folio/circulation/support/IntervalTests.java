package org.folio.circulation.support;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IntervalTests {

  @AfterEach
  public void afterEach() {
    // The clock must be reset after each test.
    ClockUtil.setDefaultClock();
  }

  @Test
  void shouldCreateDefaultIntervalWithNoDurationForCurrentTime() {
    final Instant instant = Instant.parse("2020-10-20T10:20:10.000Z");
    ClockUtil.setClock(Clock.fixed(instant, UTC));

    final LocalDateTime expected = LocalDateTime.ofInstant(instant, UTC);
    final Interval result = new Interval();

    assertEquals(UTC, result.getZone());
    assertEquals(expected.toEpochSecond(UTC), result.getBegin().toEpochSecond(UTC));
    assertEquals(Duration.ofMillis(0L).toMillis(), result.getDuration().toMillis());
  }

  @Test
  void shouldCreateIntervalWithDurationBetweenZonedDateTimes() {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));

    final long millis = 12345000;
    final ZonedDateTime begin = ClockUtil.getZonedDateTime();
    final ZonedDateTime end = begin.plusSeconds(millis / 1000);

    final Interval result = new Interval(begin, end);

    assertEquals(UTC, result.getZone());
    assertEquals(begin.toLocalDateTime(), result.getBegin());
    assertEquals(millis, result.getDuration().toMillis());
  }

  @Test
  void shouldCreateIntervalWithDurationBetweenLocalDateTimes() {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));

    final long millis = 12345000;
    final LocalDateTime begin = ClockUtil.getLocalDateTime();
    final LocalDateTime end = begin.plusSeconds(millis / 1000);

    final Interval result = new Interval(begin, end);

    assertEquals(UTC, result.getZone());
    assertEquals(begin, result.getBegin());
    assertEquals(millis, result.getDuration().toMillis());
  }

  @Test
  void shouldCreateIntervalWithDurationBetweenMillis() {
    final Instant instant = Instant.parse("2020-10-20T10:20:10.000Z");
    ClockUtil.setClock(Clock.fixed(instant, UTC));

    final long millis = 12345000;
    final long begin = instant.toEpochMilli();
    final long end = instant.plusSeconds(millis / 1000).toEpochMilli();

    final Interval result = new Interval(begin, end);

    assertEquals(UTC, result.getZone());
    assertEquals(instant.truncatedTo(ChronoUnit.SECONDS).toEpochMilli() / 1000,
      result.getBegin().toEpochSecond(UTC));
    assertEquals(millis, result.getDuration().toMillis());
  }

  @Test
  void shouldCreateIntervalWithDurationBetweenMillisWithZone() {
    final Instant instant = Instant.parse("2020-10-20T10:20:10.000Z");
    ClockUtil.setClock(Clock.fixed(instant, UTC));

    final long millis = 12345000;
    final long begin = instant.toEpochMilli();
    final long end = instant.plusSeconds(millis / 1000).toEpochMilli();

    final Interval result = new Interval(begin, end, UTC);

    assertEquals(UTC, result.getZone());
    assertEquals(instant.truncatedTo(ChronoUnit.SECONDS).toEpochMilli() / 1000,
      result.getBegin().toEpochSecond(UTC));
    assertEquals(millis, result.getDuration().toMillis());
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, -20000, false",
    "1, 0, true",
    "2, 80, true",
    "3, 12345, false",
    "4, 20000, false",
  })
  void shouldDetermineIfDifferenceInDatesAreContainedWithinDuration(int id, long differenceInSeconds, boolean expected) {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));

    final long millis = 12345000;
    final ZonedDateTime begin = ClockUtil.getZonedDateTime();
    final ZonedDateTime end = begin.plusSeconds(millis / 1000);
    final ZonedDateTime dateToCheck = begin.plusSeconds(differenceInSeconds);

    final Interval interval = new Interval(begin, end);

    assertEquals(expected, interval.contains(dateToCheck), "For test " + id);
  }

  @Test
  void shouldGetStartZonedDateTimeFromInterval() {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));

    final long millis = 12345000;
    final ZonedDateTime begin = ClockUtil.getZonedDateTime();
    final ZonedDateTime end = begin.plusSeconds(millis / 1000);

    final Interval interval = new Interval(begin, end);

    assertEquals(begin, interval.getStart());
  }

  @Test
  void shouldGetEndZonedDateTimeFromInterval() {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));

    final long millis = 12345000;
    final ZonedDateTime begin = ClockUtil.getZonedDateTime();
    final ZonedDateTime end = begin.plusSeconds(millis / 1000);

    final Interval interval = new Interval(begin, end);

    assertEquals(end, interval.getEnd());
  }

  @Test
  void shouldGetZoneIdFromInterval() {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));

    final long millis = 12345000;
    final ZonedDateTime begin = ClockUtil.getZonedDateTime();
    final ZonedDateTime end = begin.plusSeconds(millis / 1000);

    final Interval interval = new Interval(begin, end);

    assertEquals(UTC, interval.getZone());
  }

  @Test
  void shouldDetermineIntervalsDoOverlap() {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));

    final long millis = 12345000;
    final ZonedDateTime begin = ClockUtil.getZonedDateTime();
    final ZonedDateTime end = begin.plusSeconds(millis / 1000);
    final ZonedDateTime nextBegin = begin.plusSeconds(1);
    final ZonedDateTime nextEnd = end.plusSeconds(1);

    final Interval interval = new Interval(begin, end);
    final Interval nextInterval = new Interval(nextBegin, nextEnd);

    assertEquals(false, interval.abuts(nextInterval));
  }

  @Test
  void shouldDetermineIntervalsAbutBefore() {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));

    final long millis = 12345000;
    final ZonedDateTime begin = ClockUtil.getZonedDateTime();
    final ZonedDateTime end = begin.plusSeconds(millis / 1000);
    final ZonedDateTime nextBegin = begin.plusSeconds(-1);
    final ZonedDateTime nextEnd = begin;

    final Interval interval = new Interval(begin, end);
    final Interval nextInterval = new Interval(nextBegin, nextEnd);

    assertEquals(true, interval.abuts(nextInterval));
  }

  @Test
  void shouldDetermineIntervalsAbutAfter() {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));

    final long millis = 12345000;
    final ZonedDateTime begin = ClockUtil.getZonedDateTime();
    final ZonedDateTime end = begin.plusSeconds(millis / 1000);
    final ZonedDateTime nextBegin = end;
    final ZonedDateTime nextEnd = end.plusSeconds(1);

    final Interval interval = new Interval(begin, end);
    final Interval nextInterval = new Interval(nextBegin, nextEnd);

    assertEquals(true, interval.abuts(nextInterval));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2020-10-20T10:20:10.000Z, true",
    "1, 2020-10-20T10:20:10.100Z, false",
    "2, 2020-10-20T10:20:11.000Z, true",
    "3, 2020-10-20T10:20:11.100Z, false",
    "4, 1900-10-20T10:20:11.000Z, false",
    "5, 2100-10-20T10:20:11.000Z, false",
  }, nullValues = {"null"})
  void shouldDetermineIntervalsAbutFromNull(int id, String dateTimeForClock, boolean expected) {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));

    final ZonedDateTime begin = ClockUtil.getZonedDateTime();
    final ZonedDateTime end = begin.plusSeconds(1);

    final Interval interval = new Interval(begin, end);

    ClockUtil.setClock(Clock.fixed(Instant.parse(dateTimeForClock), UTC));

    assertEquals(expected, interval.abuts(null), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, -20000, 7654",
    "1, -12347, 1",
    "2, 0, null",
    "3, 80, 80",
    "4, 20000, 20000",
  }, nullValues = {"null"})
  void shouldCorrectlyDetermineGapSizeBetweenIntervals(int id, long differenceInSeconds, Long expected) {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));

    final long millis = 12345000;
    final ZonedDateTime begin = ClockUtil.getZonedDateTime();
    final ZonedDateTime end = begin.plusSeconds(millis / 1000);
    final ZonedDateTime nextBegin = end.plusSeconds(differenceInSeconds);
    final ZonedDateTime nextEnd = nextBegin.plusSeconds(1);

    final Interval interval = new Interval(begin, end);
    final Interval nextInterval = new Interval(nextBegin, nextEnd);
    final Interval result = interval.gap(nextInterval);

    assertEquals(expected, expected == null ? result : result.getDuration().toSeconds(), "For test " + id);
  }

}
