package org.folio.circulation.support.utils;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClockUtilTests {

  @AfterEach
  public void afterEach() {
    // The clock must be reset after each test.
    ClockUtil.setDefaultClock();
  }

  @Test
  void shouldAssignCustomClock() {
    final Instant expected = Instant.parse("2020-10-20T10:20:10.000Z");
    ClockUtil.setClock(Clock.fixed(expected, UTC));

    final ZonedDateTime result = ClockUtil.getZonedDateTime();

    assertEquals(expected.toEpochMilli(), result.toInstant().toEpochMilli());
  }

  @Test
  void shouldRestoreDefaultClock() {
    final Instant expected = Instant.parse("2020-10-20T10:20:10.000Z");
    ClockUtil.setClock(Clock.fixed(expected, UTC));

    ClockUtil.setDefaultClock();

    final ZonedDateTime result = ClockUtil.getZonedDateTime();

    assertNotEquals(expected.toEpochMilli(), result.toInstant().toEpochMilli());
  }

  @Test
  void shouldGetClock() {
    final Instant instant = Instant.parse("2020-10-20T10:20:10.000Z");
    final Clock expected = Clock.fixed(instant, UTC);
    ClockUtil.setClock(expected);

    final Clock result = ClockUtil.getClock();

    assertEquals(expected, result);
  }

  @Test
  void shouldGetZonedDateTime() {
    final Instant expected = Instant.parse("2020-10-20T10:20:10.000Z");
    ClockUtil.setClock(Clock.fixed(expected, UTC));

    final ZonedDateTime result = ClockUtil.getZonedDateTime();

    assertEquals(expected.toEpochMilli(), result.toInstant().toEpochMilli());
  }

  @Test
  void shouldGetLocalDateTime() {
    final Instant instant = Instant.parse("2020-10-20T10:20:10.000Z");
    ClockUtil.setClock(Clock.fixed(instant, UTC));

    final LocalDateTime expected = LocalDateTime.ofInstant(instant, UTC);

    final LocalDateTime result = ClockUtil.getLocalDateTime();

    assertEquals(expected.toEpochSecond(UTC), result.toEpochSecond(UTC));
  }

  @Test
  void shouldGetLocalDate() {
    final Instant instant = Instant.parse("2020-10-20T10:20:10.000Z");
    ClockUtil.setClock(Clock.fixed(instant, UTC));

    final LocalDate expected = LocalDate.ofInstant(instant, UTC);

    final LocalDate result = ClockUtil.getLocalDate();

    assertEquals(expected.toEpochDay(), result.toEpochDay());
  }

  @Test
  void shouldGetLocalTime() {
    final Instant instant = Instant.parse("2020-10-20T10:20:10.000Z");
    ClockUtil.setClock(Clock.fixed(instant, UTC));

    final LocalDate date = LocalDate.ofInstant(instant, UTC);
    final LocalTime expected = LocalTime.ofInstant(instant, UTC);

    final LocalTime result = ClockUtil.getLocalTime();

    assertEquals(expected.toEpochSecond(date, UTC), result.toEpochSecond(date, UTC));
  }

  @Test
  void shouldGetInstant() {
    final Instant expected = Instant.parse("2020-10-20T10:20:10.000Z");
    ClockUtil.setClock(Clock.fixed(expected, UTC));

    final Instant result = ClockUtil.getInstant();

    assertEquals(expected.toEpochMilli(), result.toEpochMilli());
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, UTC, 2020-10-20T10:20:10.000Z",
    "1, America/New_York, 2020-10-20T10:20:10-04:00"
  }, nullValues = {"null"})
  void shouldGetZoneId(int id, String zone, ZonedDateTime dateTime) {
    final ZoneId expected = ZoneId.of(zone);
    ClockUtil.setClock(Clock.fixed(dateTime.toInstant(), expected));

    final ZoneId result = ClockUtil.getZoneId();

    assertEquals(expected, result, "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 0, 2020-10-20T10:20:10.000Z",
    "1, -4, 2020-10-20T10:20:10-04:00"
  }, nullValues = {"null"})
  void shouldGetZoneOffset(int id, Integer offset, ZonedDateTime dateTime) {
    final ZoneOffset expected = ZoneOffset.ofHours(offset);
    ClockUtil.setClock(Clock.fixed(dateTime.toInstant(), expected));

    final ZoneOffset result = ClockUtil.getZoneOffset();

    assertEquals(expected, result, "For test " + id);
  }

}
