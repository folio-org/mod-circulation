package org.folio.circulation.support.utils;

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
    final ZonedDateTime date = ZonedDateTime.parse(FORMATTED_DATE_TIME);
    final String result = DateFormatUtil.formatDate(date);

    assertEquals(FORMATTED_DATE, result);
  }

  @Test
  void shouldFormatDate() {
    final LocalDate date = LocalDate.parse(FORMATTED_DATE);
    final String result = DateFormatUtil.formatDate(date);

    assertEquals(FORMATTED_DATE, result);
  }

  @Test
  void shouldFormatZonedDateTimeOptionalWithNull() {
    final String result = DateFormatUtil.formatDateTimeOptional((ZonedDateTime) null);

    assertNull(result);
  }

  @Test
  void shouldFormatInstantDateTimeOptionalWithNull() {
    final String result = DateFormatUtil.formatDateTimeOptional((Instant) null);

    assertNull(result);
  }

  @Test
  void shouldFormatZonedDateTimeOptional() {
    final ZonedDateTime date = ZonedDateTime.parse(FORMATTED_DATE_TIME);
    final String result = DateFormatUtil.formatDateTimeOptional(date);

    assertEquals(FORMATTED_DATE_TIME, result);
  }

  @Test
  void shouldFormatInstantDateTimeOptional() {
    final Instant date = Instant.parse(FORMATTED_DATE_TIME);
    final String result = DateFormatUtil.formatDateTimeOptional(date);

    assertEquals(FORMATTED_DATE_TIME, result);
  }

  @Test
  void shouldFormatZonedDateTime() {
    final ZonedDateTime date = ZonedDateTime.parse(FORMATTED_DATE_TIME);
    final String result = DateFormatUtil.formatDateTime(date);

    assertEquals(FORMATTED_DATE_TIME, result);
  }

  @Test
  void shouldFormatInstantDateTime() {
    final Instant date = Instant.parse(FORMATTED_DATE_TIME);
    final String result = DateFormatUtil.formatDateTime(date);

    assertEquals(FORMATTED_DATE_TIME, result);
  }

  @Test
  void shouldFormatDateAsDateTime() {
    final LocalDate date = LocalDate.parse(FORMATTED_DATE);
    final String result = DateFormatUtil.formatDateTime(date);

    assertEquals(FORMATTED_DATE_TIME_FROM_DATE, result);
  }

  @Test
  void shouldParseZonedDateTime() {
    final ZonedDateTime date = ZonedDateTime.parse(FORMATTED_DATE_TIME);
    final ZonedDateTime result = DateFormatUtil.parseDateTime(FORMATTED_DATE_TIME);

    assertEquals(date, result);
  }

  @Test
  void shouldThrowExceptionDuringParseZonedDateTime() {
    assertThrows(DateTimeParseException.class, () -> {
      DateFormatUtil.parseDateTime("Invalid Date");
    });
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, " + FORMATTED_DATE_TIME_NOW,
    "1, Z, null, " + FORMATTED_DATE_TIME_NOW,
    "2, null, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "3, Z, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z"
  }, nullValues = {"null"})
  void shouldParseZonedDateTimeWithZone(int id, ZoneId zone, String value, String match) {
    final ZonedDateTime date = ZonedDateTime.parse(match);
    final ZonedDateTime result = DateFormatUtil.parseDateTime(value, zone);

    assertEquals(date, result, "For test " + id);
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
    final ZonedDateTime result = DateFormatUtil.parseDateTimeOptional(value);

    assertEquals(date, result);
  }

  void shouldThrowExceptionDuringParseZonedDateTimeOptional() {
    assertThrows(DateTimeParseException.class, () -> {
      DateFormatUtil.parseDateTimeOptional("Invalid Date");
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
    final ZonedDateTime result = DateFormatUtil.parseDateTimeOptional(value, zone);

    assertEquals(date, result, "For test " + id);
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
    final Instant result = DateFormatUtil.parseInstantOptional(value);

    assertEquals(date, result);
  }

  void shouldThrowExceptionDuringParseInstantOptional() {
    assertThrows(DateTimeParseException.class, () -> {
      DateFormatUtil.parseInstantOptional("Invalid Date");
    });
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
    final Instant result = DateFormatUtil.parseInstantOptional(value, zone);

    assertEquals(date, result, "For test " + id);
  }

  @Test
  void shouldParseZonedDate() {
    final LocalDate date = LocalDate.parse(FORMATTED_DATE);
    final LocalDate result = DateFormatUtil.parseDate(FORMATTED_DATE);

    assertEquals(date, result);
  }

  void shouldThrowExceptionDuringParseDate() {
    assertThrows(DateTimeParseException.class, () -> {
      DateFormatUtil.parseDate("Invalid Date");
    });
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, " + FORMATTED_DATE_NOW,
    "1, Z, null, " + FORMATTED_DATE_NOW,
    "2, null, 2010-10-10, 2010-10-10",
    "3, Z, 2010-10-10, 2010-10-10"
  }, nullValues = {"null"})
  void shouldParseZonedDateWithZone(int id, ZoneId zone, String value, String match) {
    final LocalDate date = LocalDate.parse(match);
    final LocalDate result = DateFormatUtil.parseDate(value, zone);

    assertEquals(date, result, "For test " + id);
  }

  @Test
  void shouldParseZonedTime() {
    final LocalTime date = LocalTime.parse(FORMATTED_LOCAL_TIME);
    final LocalTime result = DateFormatUtil.parseTime(FORMATTED_TIME);

    assertEquals(date, result);
  }

  void shouldThrowExceptionDuringParseTime() {
    assertThrows(DateTimeParseException.class, () -> {
      DateFormatUtil.parseTime("Invalid Time");
    });
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, " + FORMATTED_LOCAL_TIME_NOW,
    "1, Z, null, " + FORMATTED_LOCAL_TIME_NOW,
    "2, null, 10:10:10.000, 10:10:10.000",
    "3, Z, 10:10:10.000, 10:10:10.000"
  }, nullValues = {"null"})
  void shouldParseZonedTimeWithZone(int id, ZoneId zone, String value, String match) {
    final LocalTime date = LocalTime.parse(match);
    final LocalTime result = DateFormatUtil.parseTime(value, zone);

    assertEquals(date, result, "For test " + id);
  }

}
