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
import org.junit.jupiter.params.provider.MethodSource;

class DateFormatUtilTests {
  private static final String FORMATTED_DATE = "2010-10-10";
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
  @MethodSource("useNullAndUTCAsParameters")
  void shouldParseZonedDateTimeWithZone(int id, ZoneId zone) {
    final ZonedDateTime date = ZonedDateTime.parse(FORMATTED_DATE_TIME);
    final ZonedDateTime result = DateFormatUtil.parseDateTime(FORMATTED_DATE_TIME, zone);

    assertEquals(date, result, "For test " + id);
  }

  @ParameterizedTest
  @MethodSource("useNullAndUTCAndDateStringAndZoneForDateTimeAsParameters")
  void shouldParseJodaDateTimeOptional(int id, ZoneId zone, String value, String match) {
    ClockUtil.setClock(Clock.fixed(Instant.parse(FORMATTED_DATE_TIME_NOW), ZoneOffset.UTC));

    final ZonedDateTime date = match == null
      ? null
      : ZonedDateTime.parse(match);
    final ZonedDateTime result = DateFormatUtil.parseDateTimeOptional(value);

    assertEquals(date, result);
  }

  @ParameterizedTest
  @MethodSource("useNullAndUTCAndDateStringAndZoneForDateTimeAsParameters")
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
  @MethodSource("useNullAndUTCForJodaAsParameters")
  void shouldParseJodaDateTimeWithZone(int id, DateTimeZone zone) {
    final DateTime date = DateTime.parse(FORMATTED_DATE_TIME);
    final DateTime result = DateFormatUtil.parseJodaDateTime(FORMATTED_DATE_TIME, zone);

    assertEquals(date, result, "For test " + id);
  }

  @Test
  void shouldParseZonedDate() {
    final LocalDate date = LocalDate.parse(FORMATTED_DATE);
    final LocalDate result = DateFormatUtil.parseDate(FORMATTED_DATE);

    assertEquals(date, result);
  }

  @ParameterizedTest
  @MethodSource("useNullAndUTCAsParameters")
  void shouldParseZonedDateWithZone(int id, ZoneId zone) {
    final LocalDate date = LocalDate.parse(FORMATTED_DATE);
    final LocalDate result = DateFormatUtil.parseDate(FORMATTED_DATE, zone);

    assertEquals(date, result, "For test " + id);
  }

  @Test
  void shouldParseJodaDate() {
    final org.joda.time.LocalDate date = org.joda.time.LocalDate.parse(FORMATTED_DATE);
    final org.joda.time.LocalDate result = DateFormatUtil.parseJodaDate(FORMATTED_DATE);

    assertEquals(date, result);
  }

  @ParameterizedTest
  @MethodSource("useNullAndUTCForJodaAsParameters")
  void shouldParseJodaDateWithZone(int id, DateTimeZone zone) {
    final org.joda.time.LocalDate date = org.joda.time.LocalDate.parse(FORMATTED_DATE);
    final org.joda.time.LocalDate result = DateFormatUtil.parseJodaDate(FORMATTED_DATE, zone);

    assertEquals(date, result, "For test " + id);
  }

  private static Object[] useNullAndUTCAsParameters() {
    return new Object[] {
      new Object[] { 0, null },
      new Object[] { 1, ZoneOffset.UTC },
    };
  }

  private static Object[] useNullAndUTCForJodaAsParameters() {
    return new Object[] {
      new Object[] { 0, null },
      new Object[] { 1, DateTimeZone.UTC },
    };
  }

  private static Object[] useNullAndUTCAndDateStringAndZoneForDateTimeAsParameters() {
    return new Object[] {
      new Object[] { 0, null, null, null },
      new Object[] { 1, ZoneOffset.UTC, null, null },
      new Object[] { 2, null, FORMATTED_DATE_TIME, FORMATTED_DATE_TIME },
      new Object[] { 3, ZoneOffset.UTC, FORMATTED_DATE_TIME, FORMATTED_DATE_TIME },
    };
  }

}
