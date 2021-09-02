package org.folio.circulation.support.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class DateFormatUtilTests {
  private static final String FORMATTED_DATE = "2010-10-10";
  private static final String FORMATTED_DATE_NOW = "2020-10-20";
  private static final String FORMATTED_DATE_TIME = "2010-10-10T10:10:10.000Z";
  private static final String FORMATTED_DATE_TIME_FROM_DATE = "2010-10-10T00:00:00.000Z";
  private static final String FORMATTED_DATE_TIME_NOW = "2020-10-20T10:10:10.000Z";

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
  void shouldFormatOffsetDateTimeAsDate() {
    final OffsetDateTime date = OffsetDateTime.parse(FORMATTED_DATE_TIME);
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
  void shouldFormatJodaDate() {
    final org.joda.time.LocalDate date = org.joda.time.LocalDate.parse(FORMATTED_DATE);
    final String result = DateFormatUtil.formatDate(date);

    assertEquals(FORMATTED_DATE, result);
  }

  @Test
  void shouldFormatZonedDateTimeOptional() {
    final String result = DateFormatUtil.formatDateTimeOptional((ZonedDateTime) null);

    assertNull(result);
  }

  @Test
  void shouldFormatOffsetDateTimeOptional() {
    final String result = DateFormatUtil.formatDateTimeOptional((OffsetDateTime) null);

    assertNull(result);
  }

  @Test
  void shouldFormatJodaDateTimeOptional() {
    final String result = DateFormatUtil.formatDateTimeOptional((DateTime) null);

    assertNull(result);
  }

  @Test
  void shouldFormatZonedDateTime() {
    final ZonedDateTime date = ZonedDateTime.parse(FORMATTED_DATE_TIME);
    final String result = DateFormatUtil.formatDateTime(date);

    assertEquals(FORMATTED_DATE_TIME, result);
  }

  @Test
  void shouldFormatOffsetDateTime() {
    final OffsetDateTime date = OffsetDateTime.parse(FORMATTED_DATE_TIME);
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
  void shouldFormatJodaDateTime() {
    final DateTime date = DateTime.parse(FORMATTED_DATE_TIME);
    final String result = DateFormatUtil.formatDateTime(date);

    assertEquals(FORMATTED_DATE_TIME, result);
  }

  @Test
  void shouldParseZonedDateTime() {
    final ZonedDateTime date = ZonedDateTime.parse(FORMATTED_DATE_TIME);
    final ZonedDateTime result = DateFormatUtil.parseDateTime(FORMATTED_DATE_TIME);

    assertEquals(date, result);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, " + FORMATTED_DATE_TIME_NOW,
    "1, Z, null, " + FORMATTED_DATE_TIME_NOW,
    "2, null, " + FORMATTED_DATE_TIME + ", " + FORMATTED_DATE_TIME,
    "3, Z, " + FORMATTED_DATE_TIME + ", " + FORMATTED_DATE_TIME
  }, nullValues = {"null"})
  void shouldParseZonedDateTimeWithZone(int id, ZoneId zone, String value, String match) {
    ClockUtil.setClock(Clock.fixed(Instant.parse(FORMATTED_DATE_TIME_NOW), ZoneOffset.UTC));

    final ZonedDateTime date = ZonedDateTime.parse(match);
    final ZonedDateTime result = DateFormatUtil.parseDateTime(value, zone);

    assertEquals(date, result, "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null",
    "1, UTC, null, null",
    "2, null, " + FORMATTED_DATE_TIME + ", " + FORMATTED_DATE_TIME,
    "3, UTC, " + FORMATTED_DATE_TIME + ", " + FORMATTED_DATE_TIME
  }, nullValues = {"null"})
  void shouldParseJodaDateTimeOptional(int id, ZoneId zone, String value, String match) {
    ClockUtil.setClock(Clock.fixed(Instant.parse(FORMATTED_DATE_TIME_NOW), ZoneOffset.UTC));

    final ZonedDateTime date = match == null
      ? null
      : ZonedDateTime.parse(match);
    final ZonedDateTime result = DateFormatUtil.parseDateTimeOptional(value);

    assertEquals(date, result);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null",
    "1, Z, null, null",
    "2, null, " + FORMATTED_DATE_TIME + ", " + FORMATTED_DATE_TIME,
    "3, Z, " + FORMATTED_DATE_TIME + ", " + FORMATTED_DATE_TIME
  }, nullValues = {"null"})
  void shouldParseZonedDateTimeOptionalWithZone(int id, ZoneId zone, String value, String match) {
    ClockUtil.setClock(Clock.fixed(Instant.parse(FORMATTED_DATE_TIME_NOW), ZoneOffset.UTC));

    final ZonedDateTime date = match == null
      ? null
      : ZonedDateTime.parse(match);
    final ZonedDateTime result = DateFormatUtil.parseDateTimeOptional(value, zone);

    assertEquals(date, result, "For test " + id);
  }

  @Test
  void shouldParseJodaDateTime() {
    final DateTime date = DateTime.parse(FORMATTED_DATE_TIME);
    final DateTime result = DateFormatUtil.parseJodaDateTime(FORMATTED_DATE_TIME);

    assertEquals(date, result);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, " + FORMATTED_DATE_TIME_NOW,
    "1, UTC, null, " + FORMATTED_DATE_TIME_NOW,
    "2, null, " + FORMATTED_DATE_TIME + ", " + FORMATTED_DATE_TIME,
    "3, UTC, " + FORMATTED_DATE_TIME + ", " + FORMATTED_DATE_TIME
  }, nullValues = {"null"})
  void shouldParseJodaDateTimeWithZone(int id, DateTimeZone zone, String value, String match) {
    ClockUtil.setClock(Clock.fixed(Instant.parse(FORMATTED_DATE_TIME_NOW), ZoneOffset.UTC));

    final DateTime date = DateTime.parse(match);
    final DateTime result = DateFormatUtil.parseJodaDateTime(value, zone);

    assertEquals(date, result, "For test " + id);
  }

  @Test
  void shouldParseZonedDate() {
    final LocalDate date = LocalDate.parse(FORMATTED_DATE);
    final LocalDate result = DateFormatUtil.parseDate(FORMATTED_DATE);

    assertEquals(date, result);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, " + FORMATTED_DATE_NOW,
    "1, UTC, null, " + FORMATTED_DATE_NOW,
    "2, null, " + FORMATTED_DATE + ", " + FORMATTED_DATE,
    "3, UTC, " + FORMATTED_DATE + ", " + FORMATTED_DATE
  }, nullValues = {"null"})
  void shouldParseZonedDateWithZone(int id, ZoneId zone, String value, String match) {
    ClockUtil.setClock(Clock.fixed(Instant.parse(FORMATTED_DATE_TIME_NOW), ZoneOffset.UTC));

    final LocalDate date = LocalDate.parse(match);
    final LocalDate result = DateFormatUtil.parseDate(value, zone);

    assertEquals(date, result, "For test " + id);
  }

  @Test
  void shouldParseJodaDate() {
    final org.joda.time.LocalDate date = org.joda.time.LocalDate.parse(FORMATTED_DATE);
    final org.joda.time.LocalDate result = DateFormatUtil.parseJodaDate(FORMATTED_DATE);

    assertEquals(date, result);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, " + FORMATTED_DATE_NOW,
    "1, UTC, null, " + FORMATTED_DATE_NOW,
    "2, null, " + FORMATTED_DATE + ", " + FORMATTED_DATE,
    "3, UTC, " + FORMATTED_DATE + ", " + FORMATTED_DATE
  }, nullValues = {"null"})
  void shouldParseJodaDateWithZone(int id, DateTimeZone zone) {
    ClockUtil.setClock(Clock.fixed(Instant.parse(FORMATTED_DATE_TIME_NOW), ZoneOffset.UTC));

    final org.joda.time.LocalDate date = org.joda.time.LocalDate.parse(FORMATTED_DATE);
    final org.joda.time.LocalDate result = DateFormatUtil.parseJodaDate(FORMATTED_DATE, zone);

    assertEquals(date, result, "For test " + id);
  }

}
