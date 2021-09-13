package org.folio.circulation.support.utils;

import static org.folio.circulation.support.utils.DateFormatUtil.formatDate;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTimeOptional;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDate;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDateTime;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDateTimeOptional;
import static org.folio.circulation.support.utils.DateFormatUtil.parseInstantOptional;
import static org.folio.circulation.support.utils.DateFormatUtil.parseTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DateFormatUtilTests {
  private static final String FORMATTED_DATE = "2010-10-10";
  private static final String FORMATTED_DATE_NOW = "2020-10-20";
  private static final String FORMATTED_DATE_TIME = "2010-10-10T10:20:10.000Z";
  private static final String FORMATTED_DATE_TIME_FROM_DATE = "2010-10-10T00:00:00.000Z";
  private static final String FORMATTED_DATE_TIME_NOW = "2020-10-20T10:20:10.000Z";
  private static final String FORMATTED_TIME = "10:10:10.000Z";
  private static final String FORMATTED_LOCAL_TIME = "10:10:10.000";
  private static final String FORMATTED_LOCAL_TIME_NOW = "10:20:10.000";

  @BeforeEach
  public void beforeEach() {
    ClockUtil.setClock(Clock.fixed(Instant.parse(FORMATTED_DATE_TIME_NOW), ZoneOffset.UTC));
  }

  @AfterEach
  public void afterEach() {
    // The clock must be reset after each test.
    ClockUtil.setDefaultClock();
  }

  @Test
  void shouldFormatZonedDateTimeAsDate() {
    assertEquals(FORMATTED_DATE, formatDate(ZonedDateTime.parse(FORMATTED_DATE_TIME)));
  }

  @Test
  void shouldFormatDate() {
    assertEquals(FORMATTED_DATE, formatDate(LocalDate.parse(FORMATTED_DATE)));
  }

  @Test
  void shouldFormatZonedDateTimeOptionalWithNull() {
    assertNull(formatDateTimeOptional((ZonedDateTime) null));
  }

  @Test
  void shouldFormatInstantDateTimeOptionalWithNull() {
    assertNull(formatDateTimeOptional((Instant) null));
  }

  @Test
  void shouldFormatZonedDateTimeOptional() {
    assertEquals(FORMATTED_DATE_TIME,
      formatDateTimeOptional(ZonedDateTime.parse(FORMATTED_DATE_TIME)));
  }

  @Test
  void shouldFormatInstantDateTimeOptional() {
    assertEquals(FORMATTED_DATE_TIME, formatDateTimeOptional(Instant.parse(FORMATTED_DATE_TIME)));
  }

  @Test
  void shouldFormatZonedDateTime() {
    assertEquals(FORMATTED_DATE_TIME, formatDateTime(ZonedDateTime.parse(FORMATTED_DATE_TIME)));
  }

  @Test
  void shouldFormatInstantDateTime() {
    assertEquals(FORMATTED_DATE_TIME, formatDateTime(Instant.parse(FORMATTED_DATE_TIME)));
  }

  @Test
  void shouldFormatDateAsDateTime() {
    assertEquals(FORMATTED_DATE_TIME_FROM_DATE, formatDateTime(LocalDate.parse(FORMATTED_DATE)));
  }

  @Test
  void shouldParseZonedDateTime() {
    assertEquals(ZonedDateTime.parse(FORMATTED_DATE_TIME), parseDateTime(FORMATTED_DATE_TIME));
  }

  @Test
  void shouldThrowExceptionDuringParseZonedDateTime() {
    assertThrows(DateTimeParseException.class, () -> parseDateTime("Invalid Date"));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, " + FORMATTED_DATE_TIME_NOW,
    "1, Z, null, " + FORMATTED_DATE_TIME_NOW,
    "2, null, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "3, Z, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z"
  }, nullValues = {"null"})
  void shouldParseZonedDateTimeWithZone(int id, ZoneId zone, String value, String match) {
    assertEquals(ZonedDateTime.parse(match), parseDateTime(value, zone), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null",
    "1, UTC, null, null",
    "2, null, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "3, UTC, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z"
  }, nullValues = {"null"})
  void shouldParseZonedDateTimeOptional(int id, ZoneId zone, String value, String match) {
    final ZonedDateTime date = match == null
      ? null
      : ZonedDateTime.parse(match);

    assertEquals(date, parseDateTimeOptional(value));
  }

  void shouldThrowExceptionDuringParseZonedDateTimeOptional() {
    assertThrows(DateTimeParseException.class, () -> {
      parseDateTimeOptional("Invalid Date");
    });
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null",
    "1, Z, null, null",
    "2, null, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "3, Z, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z"
  }, nullValues = {"null"})
  void shouldParseZonedDateTimeOptionalWithZone(int id, ZoneId zone, String value, String match) {
    final ZonedDateTime date = match == null
      ? null
      : ZonedDateTime.parse(match);

    assertEquals(date, parseDateTimeOptional(value, zone), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null",
    "1, UTC, null, null",
    "2, null, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "3, UTC, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z"
  }, nullValues = {"null"})
  void shouldParseInstantOptional(int id, ZoneId zone, String value, String match) {
    final Instant date = match == null
      ? null
      : ZonedDateTime.parse(match).toInstant();

    assertEquals(date, parseInstantOptional(value));
  }

  void shouldThrowExceptionDuringParseInstantOptional() {
    assertThrows(DateTimeParseException.class, () -> parseInstantOptional("Invalid Date"));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null",
    "1, Z, null, null",
    "2, null, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "3, Z, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z"
  }, nullValues = {"null"})
  void shouldParseInstantOptionalWithZone(int id, ZoneId zone, String value, String match) {
    final Instant date = match == null
      ? null
      : ZonedDateTime.parse(match).toInstant();

    assertEquals(date, parseInstantOptional(value, zone), "For test " + id);
  }

  @Test
  void shouldParseZonedDate() {
    assertEquals(LocalDate.parse(FORMATTED_DATE), parseDate(FORMATTED_DATE));
  }

  void shouldThrowExceptionDuringParseDate() {
    assertThrows(DateTimeParseException.class, () -> parseDate("Invalid Date"));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, " + FORMATTED_DATE_NOW,
    "1, Z, null, " + FORMATTED_DATE_NOW,
    "2, null, 2010-10-10, 2010-10-10",
    "3, Z, 2010-10-10, 2010-10-10"
  }, nullValues = {"null"})
  void shouldParseZonedDateWithZone(int id, ZoneId zone, String value, String match) {
    assertEquals(LocalDate.parse(match), parseDate(value, zone), "For test " + id);
  }

  @Test
  void shouldParseZonedTime() {
    assertEquals(LocalTime.parse(FORMATTED_LOCAL_TIME), parseTime(FORMATTED_TIME));
  }

  void shouldThrowExceptionDuringParseTime() {
    assertThrows(DateTimeParseException.class, () -> parseTime("Invalid Time"));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, " + FORMATTED_LOCAL_TIME_NOW,
    "1, Z, null, " + FORMATTED_LOCAL_TIME_NOW,
    "2, null, 10:10:10.000, 10:10:10.000",
    "3, Z, 10:10:10.000, 10:10:10.000"
  }, nullValues = {"null"})
  void shouldParseZonedTimeWithZone(int id, ZoneId zone, String value, String match) {
    assertEquals(LocalTime.parse(match), parseTime(value, zone), "For test " + id);
  }

}
