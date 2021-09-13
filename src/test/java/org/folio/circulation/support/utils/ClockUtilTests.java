package org.folio.circulation.support.utils;

import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.ClockUtil.getInstant;
import static org.folio.circulation.support.utils.ClockUtil.getLocalDate;
import static org.folio.circulation.support.utils.ClockUtil.getLocalDateTime;
import static org.folio.circulation.support.utils.ClockUtil.getLocalTime;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClockUtilTests {
  private static final Instant INSTANT = Instant.parse("2020-10-20T10:20:10.000Z");

  @BeforeEach
  public void BeforeEach() {
    ClockUtil.setClock(Clock.fixed(INSTANT, UTC));
  }

  @AfterEach
  public void afterEach() {
    // The clock must be reset after each test.
    ClockUtil.setDefaultClock();
  }

  @Test
  void shouldAssignCustomClock() {
    assertEquals(INSTANT.toEpochMilli(), getZonedDateTime().toInstant().toEpochMilli());
  }

  @Test
  void shouldRestoreDefaultClock() {
    ClockUtil.setDefaultClock();

    assertNotEquals(INSTANT.toEpochMilli(), getZonedDateTime().toInstant().toEpochMilli());
  }

  @Test
  void shouldGetClock() {
    Clock expected = Clock.fixed(INSTANT, UTC);
    ClockUtil.setClock(expected);

    assertEquals(expected, ClockUtil.getClock());
  }

  @Test
  void shouldGetZonedDateTime() {
    assertEquals(INSTANT.toEpochMilli(), getZonedDateTime().toInstant().toEpochMilli());
  }

  @Test
  void shouldGetLocalDateTime() {
    assertEquals(LocalDateTime.ofInstant(INSTANT, UTC).toEpochSecond(UTC),
      getLocalDateTime().toEpochSecond(UTC));
  }

  @Test
  void shouldGetLocalDate() {
    assertEquals(LocalDate.ofInstant(INSTANT, UTC).toEpochDay(), getLocalDate().toEpochDay());
  }

  @Test
  void shouldGetLocalTime() {
    final LocalDate date = LocalDate.ofInstant(INSTANT, UTC);

    assertEquals(LocalTime.ofInstant(INSTANT, UTC).toEpochSecond(date, UTC),
      getLocalTime().toEpochSecond(date, UTC));
  }

  @Test
  void shouldGetInstant() {
    assertEquals(INSTANT.toEpochMilli(), getInstant().toEpochMilli());
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, UTC, 2020-10-20T10:20:10.000Z",
    "1, America/New_York, 2020-10-20T10:20:10-04:00"
  }, nullValues = {"null"})
  void shouldGetZoneId(int id, String zone, ZonedDateTime dateTime) {
    final ZoneId expected = ZoneId.of(zone);
    ClockUtil.setClock(Clock.fixed(dateTime.toInstant(), expected));

    assertEquals(expected, ClockUtil.getZoneId(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 0, 2020-10-20T10:20:10.000Z",
    "1, -4, 2020-10-20T10:20:10-04:00"
  }, nullValues = {"null"})
  void shouldGetZoneOffset(int id, Integer offset, ZonedDateTime dateTime) {
    final ZoneOffset expected = ZoneOffset.ofHours(offset);
    ClockUtil.setClock(Clock.fixed(dateTime.toInstant(), expected));

    assertEquals(expected, ClockUtil.getZoneOffset(), "For test " + id);
  }

}
