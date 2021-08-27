package org.folio.circulation.support.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DateTimeUtilsTests {

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfYearForZonedDateTimeParameters")
  void shouldGetCorrectStartOfYearForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = zone == null
      ? DateTimeUtil.atStartOfYear(from)
      : DateTimeUtil.atStartOfYear(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfYearForZonedDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, ZonedDateTime.parse("2018-03-01T02:28:19-05:00"), ZonedDateTime.parse("2018-01-01T00:00:00-05:00"), null },
      new Object[] { 1, ZonedDateTime.parse("2018-02-28T21:28:19+00:00"), ZonedDateTime.parse("2018-01-01T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, ZonedDateTime.parse("2018-01-01T00:14:03-05:00"), ZonedDateTime.parse("2018-01-01T00:00:00-05:00"), null },
      new Object[] { 3, ZonedDateTime.parse("2018-01-01T00:14:03+00:00"), ZonedDateTime.parse("2017-01-01T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 4, ZonedDateTime.parse("2018-12-31T23:40:50+01:00"), ZonedDateTime.parse("2018-01-01T00:00:00+01:00"), null },
      new Object[] { 5, ZonedDateTime.parse("2018-12-31T23:40:50+00:00"), ZonedDateTime.parse("2019-01-01T00:00:00+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfYearForZonedDateTimeParameters")
  void shouldGetCorrectEndOfYearForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = zone == null
      ? DateTimeUtil.atEndOfYear(from)
      : DateTimeUtil.atEndOfYear(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfYearForZonedDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, ZonedDateTime.parse("2018-03-01T02:28:19-05:00"), ZonedDateTime.parse("2018-12-31T23:59:59-05:00"), null },
      new Object[] { 1, ZonedDateTime.parse("2018-02-28T21:28:19+00:00"), ZonedDateTime.parse("2018-12-31T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, ZonedDateTime.parse("2018-01-01T00:14:03-05:00"), ZonedDateTime.parse("2018-12-31T23:59:59-05:00"), null },
      new Object[] { 3, ZonedDateTime.parse("2018-01-01T00:14:03+00:00"), ZonedDateTime.parse("2017-12-31T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 4, ZonedDateTime.parse("2018-12-31T23:40:50+01:00"), ZonedDateTime.parse("2018-12-31T23:59:59+01:00"), null },
      new Object[] { 5, ZonedDateTime.parse("2018-12-31T23:40:50+00:00"), ZonedDateTime.parse("2019-12-31T23:59:59+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfYearForLocalDateParameters")
  void shouldGetCorrectStartOfYearForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atStartOfYear(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfYearForLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, LocalDate.parse("2018-02-28"), ZonedDateTime.parse("2018-01-01T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 1, LocalDate.parse("2018-01-01"), ZonedDateTime.parse("2018-01-01T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, LocalDate.parse("2018-12-31"), ZonedDateTime.parse("2018-01-01T00:00:00+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfYearForLocalDateParameters")
  void shouldGetCorrectEndOfYearForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atEndOfYear(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfYearForLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, LocalDate.parse("2018-02-28"), ZonedDateTime.parse("2018-12-31T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 1, LocalDate.parse("2018-01-01"), ZonedDateTime.parse("2018-12-31T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, LocalDate.parse("2018-12-31"), ZonedDateTime.parse("2018-12-31T23:59:59+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfYearForDateTimeParameters")
  void shouldGetCorrectStartOfYearForDateTime(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = zone == null
      ? DateTimeUtil.atStartOfYear(from)
      : DateTimeUtil.atStartOfYear(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfYearForDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, DateTime.parse("2018-03-01T02:28:19-05:00"), DateTime.parse("2018-01-01T00:00:00-05:00"), null },
      new Object[] { 1, DateTime.parse("2018-02-28T21:28:19+00:00"), DateTime.parse("2018-01-01T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, DateTime.parse("2018-01-01T00:14:03-05:00"), DateTime.parse("2018-01-01T00:00:00-05:00"), null },
      new Object[] { 3, DateTime.parse("2018-01-01T00:14:03+00:00"), DateTime.parse("2017-01-01T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 4, DateTime.parse("2018-12-31T23:40:50+01:00"), DateTime.parse("2018-01-01T00:00:00+01:00"), null },
      new Object[] { 5, DateTime.parse("2018-12-31T23:40:50+00:00"), DateTime.parse("2019-01-01T00:00:00+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfYearForDateTimeParameters")
  void shouldGetCorrectEndOfYearForDateTime(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = zone == null
      ? DateTimeUtil.atEndOfYear(from)
      : DateTimeUtil.atEndOfYear(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfYearForDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, DateTime.parse("2018-03-01T02:28:19-05:00"), DateTime.parse("2018-12-31T23:59:59-05:00"), null },
      new Object[] { 1, DateTime.parse("2018-02-28T21:28:19+00:00"), DateTime.parse("2018-12-31T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, DateTime.parse("2018-01-01T00:14:03-05:00"), DateTime.parse("2018-12-31T23:59:59-05:00"), null },
      new Object[] { 3, DateTime.parse("2018-01-01T00:14:03+00:00"), DateTime.parse("2017-12-31T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 4, DateTime.parse("2018-12-31T23:40:50+01:00"), DateTime.parse("2018-12-31T23:59:59+01:00"), null },
      new Object[] { 5, DateTime.parse("2018-12-31T23:40:50+00:00"), DateTime.parse("2019-12-31T23:59:59+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfYearForJodaLocalDateParameters")
  void shouldGetCorrectStartOfYearForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atStartOfYear(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfYearForJodaLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, org.joda.time.LocalDate.parse("2018-02-28"), DateTime.parse("2018-01-01T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 1, org.joda.time.LocalDate.parse("2018-01-01"), DateTime.parse("2018-01-01T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, org.joda.time.LocalDate.parse("2018-12-31"), DateTime.parse("2018-01-01T00:00:00+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfYearForJodaLocalDateParameters")
  void shouldGetCorrectEndOfYearForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atEndOfYear(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfYearForJodaLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, org.joda.time.LocalDate.parse("2018-02-28"), DateTime.parse("2018-12-31T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 1, org.joda.time.LocalDate.parse("2018-01-01"), DateTime.parse("2018-12-31T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, org.joda.time.LocalDate.parse("2018-12-31"), DateTime.parse("2018-12-31T23:59:59+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfMonthForZonedDateTimeParameters")
  void shouldGetCorrectStartOfMonthForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = zone == null
      ? DateTimeUtil.atStartOfMonth(from)
      : DateTimeUtil.atStartOfMonth(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfMonthForZonedDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, ZonedDateTime.parse("2018-03-01T02:28:19-05:00"), ZonedDateTime.parse("2018-03-01T00:00:00-05:00"), null },
      new Object[] { 1, ZonedDateTime.parse("2018-02-28T21:28:19+00:00"), ZonedDateTime.parse("2018-02-01T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, ZonedDateTime.parse("2018-01-01T00:14:03-05:00"), ZonedDateTime.parse("2018-01-01T00:00:00-05:00"), null },
      new Object[] { 3, ZonedDateTime.parse("2018-01-01T00:14:03+00:00"), ZonedDateTime.parse("2017-12-01T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 4, ZonedDateTime.parse("2018-12-31T23:40:50+01:00"), ZonedDateTime.parse("2018-12-01T00:00:00+01:00"), null },
      new Object[] { 5, ZonedDateTime.parse("2018-12-31T23:40:50+00:00"), ZonedDateTime.parse("2019-01-01T00:00:00+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfMonthForZonedDateTimeParameters")
  void shouldGetCorrectEndOfMonthForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = zone == null
      ? DateTimeUtil.atEndOfMonth(from)
      : DateTimeUtil.atEndOfMonth(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfMonthForZonedDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, ZonedDateTime.parse("2018-03-01T02:28:19-05:00"), ZonedDateTime.parse("2018-03-31T23:59:59-05:00"), null },
      new Object[] { 1, ZonedDateTime.parse("2018-02-28T21:28:19+00:00"), ZonedDateTime.parse("2018-02-28T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, ZonedDateTime.parse("2018-01-01T00:14:03-05:00"), ZonedDateTime.parse("2018-01-31T23:59:59-05:00"), null },
      new Object[] { 3, ZonedDateTime.parse("2018-01-01T00:14:03+00:00"), ZonedDateTime.parse("2017-12-31T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 4, ZonedDateTime.parse("2018-12-31T23:40:50+01:00"), ZonedDateTime.parse("2018-12-31T23:59:59+01:00"), null },
      new Object[] { 5, ZonedDateTime.parse("2018-12-31T23:40:50+00:00"), ZonedDateTime.parse("2019-01-31T23:59:59+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfMonthForLocalDateParameters")
  void shouldGetCorrectStartOfMonthForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atStartOfMonth(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfMonthForLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, LocalDate.parse("2018-02-28"), ZonedDateTime.parse("2018-02-01T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 1, LocalDate.parse("2018-01-01"), ZonedDateTime.parse("2018-01-01T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, LocalDate.parse("2018-12-31"), ZonedDateTime.parse("2018-12-01T00:00:00+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfMonthForLocalDateParameters")
  void shouldGetCorrectEndOfMonthForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atEndOfMonth(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfMonthForLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, LocalDate.parse("2018-02-28"), ZonedDateTime.parse("2018-02-28T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 1, LocalDate.parse("2018-01-01"), ZonedDateTime.parse("2018-01-31T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, LocalDate.parse("2018-12-31"), ZonedDateTime.parse("2018-12-31T23:59:59+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfMonthForDateTimeParameters")
  void shouldGetCorrectStartOfMonthForDateTime(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = zone == null
      ? DateTimeUtil.atStartOfMonth(from)
      : DateTimeUtil.atStartOfMonth(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfMonthForDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, DateTime.parse("2018-03-01T02:28:19-05:00"), DateTime.parse("2018-03-01T00:00:00-05:00"), null },
      new Object[] { 1, DateTime.parse("2018-02-28T21:28:19+00:00"), DateTime.parse("2018-02-01T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, DateTime.parse("2018-01-01T00:14:03-05:00"), DateTime.parse("2018-01-01T00:00:00-05:00"), null },
      new Object[] { 3, DateTime.parse("2018-01-01T00:14:03+00:00"), DateTime.parse("2017-12-01T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 4, DateTime.parse("2018-12-31T23:40:50+01:00"), DateTime.parse("2018-12-01T00:00:00+01:00"), null },
      new Object[] { 5, DateTime.parse("2018-12-31T23:40:50+00:00"), DateTime.parse("2019-01-01T00:00:00+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfMonthForDateTimeParameters")
  void shouldGetCorrectEndOfMonthForDateTime(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = zone == null
      ? DateTimeUtil.atEndOfMonth(from)
      : DateTimeUtil.atEndOfMonth(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfMonthForDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, DateTime.parse("2018-03-01T02:28:19-05:00"), DateTime.parse("2018-03-31T23:59:59-05:00"), null },
      new Object[] { 1, DateTime.parse("2018-02-28T21:28:19+00:00"), DateTime.parse("2018-02-28T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, DateTime.parse("2018-01-01T00:14:03-05:00"), DateTime.parse("2018-01-31T23:59:59-05:00"), null },
      new Object[] { 3, DateTime.parse("2018-01-01T00:14:03+00:00"), DateTime.parse("2017-12-31T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 4, DateTime.parse("2018-12-31T23:40:50+01:00"), DateTime.parse("2018-12-31T23:59:59+01:00"), null },
      new Object[] { 5, DateTime.parse("2018-12-31T23:40:50+00:00"), DateTime.parse("2019-01-31T23:59:59+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfMonthForJodaLocalDateParameters")
  void shouldGetCorrectStartOfMonthForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atStartOfMonth(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfMonthForJodaLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, org.joda.time.LocalDate.parse("2018-02-28"), DateTime.parse("2018-02-01T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 1, org.joda.time.LocalDate.parse("2018-01-01"), DateTime.parse("2018-01-01T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, org.joda.time.LocalDate.parse("2018-12-31"), DateTime.parse("2018-12-01T00:00:00+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfMonthForJodaLocalDateParameters")
  void shouldGetCorrectEndOfMonthForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atEndOfMonth(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfMonthForJodaLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, org.joda.time.LocalDate.parse("2018-02-28"), DateTime.parse("2018-02-28T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 1, org.joda.time.LocalDate.parse("2018-01-01"), DateTime.parse("2018-01-31T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, org.joda.time.LocalDate.parse("2018-12-31"), DateTime.parse("2018-12-31T23:59:59+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfDayForZonedDateTimeParameters")
  void shouldGetCorrectStartOfDayForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = zone == null
      ? DateTimeUtil.atStartOfDay(from)
      : DateTimeUtil.atStartOfDay(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfDayForZonedDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, ZonedDateTime.parse("2018-03-01T02:28:19-05:00"), ZonedDateTime.parse("2018-03-01T00:00:00-05:00"), null },
      new Object[] { 1, ZonedDateTime.parse("2018-02-28T21:28:19+00:00"), ZonedDateTime.parse("2018-02-28T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, ZonedDateTime.parse("2018-01-01T00:14:03-05:00"), ZonedDateTime.parse("2018-01-01T00:00:00-05:00"), null },
      new Object[] { 3, ZonedDateTime.parse("2018-01-01T00:14:03+00:00"), ZonedDateTime.parse("2017-12-31T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 4, ZonedDateTime.parse("2018-12-31T23:40:50+01:00"), ZonedDateTime.parse("2018-12-31T00:00:00+01:00"), null },
      new Object[] { 5, ZonedDateTime.parse("2018-12-31T23:40:50+00:00"), ZonedDateTime.parse("2019-01-01T00:00:00+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfDayForZonedDateTimeParameters")
  void shouldGetCorrectEndOfDayForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = zone == null
      ? DateTimeUtil.atEndOfDay(from)
      : DateTimeUtil.atEndOfDay(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfDayForZonedDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, ZonedDateTime.parse("2018-03-01T02:28:19-05:00"), ZonedDateTime.parse("2018-03-01T23:59:59-05:00"), null },
      new Object[] { 1, ZonedDateTime.parse("2018-02-28T21:28:19+00:00"), ZonedDateTime.parse("2018-02-28T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, ZonedDateTime.parse("2018-01-01T00:14:03-05:00"), ZonedDateTime.parse("2018-01-01T23:59:59-05:00"), null },
      new Object[] { 3, ZonedDateTime.parse("2018-01-01T00:14:03+00:00"), ZonedDateTime.parse("2017-12-31T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 4, ZonedDateTime.parse("2018-12-31T23:40:50+01:00"), ZonedDateTime.parse("2018-12-31T23:59:59+01:00"), null },
      new Object[] { 5, ZonedDateTime.parse("2018-12-31T23:40:50+00:00"), ZonedDateTime.parse("2019-01-01T23:59:59+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfDayForLocalDateParameters")
  void shouldGetCorrectStartOfDayForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atStartOfDay(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfDayForLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, LocalDate.parse("2018-02-28"), ZonedDateTime.parse("2018-02-28T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 1, LocalDate.parse("2018-01-01"), ZonedDateTime.parse("2018-01-01T00:00:00-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, LocalDate.parse("2018-12-31"), ZonedDateTime.parse("2018-12-31T00:00:00+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfDayForLocalDateParameters")
  void shouldGetCorrectEndOfDayForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atEndOfDay(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfDayForLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, LocalDate.parse("2018-02-28"), ZonedDateTime.parse("2018-02-28T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 1, LocalDate.parse("2018-01-01"), ZonedDateTime.parse("2018-01-01T23:59:59-05:00"), ZoneId.of("America/New_York") },
      new Object[] { 2, LocalDate.parse("2018-12-31"), ZonedDateTime.parse("2018-12-31T23:59:59+01:00"), ZoneId.of("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfDayForDateTimeParameters")
  void shouldGetCorrectStartOfDayForDateTime(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = zone == null
      ? DateTimeUtil.atStartOfDay(from)
      : DateTimeUtil.atStartOfDay(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfDayForDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, DateTime.parse("2018-03-01T02:28:19-05:00"), DateTime.parse("2018-03-01T00:00:00-05:00"), null },
      new Object[] { 1, DateTime.parse("2018-02-28T21:28:19+00:00"), DateTime.parse("2018-02-28T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, DateTime.parse("2018-01-01T00:14:03-05:00"), DateTime.parse("2018-01-01T00:00:00-05:00"), null },
      new Object[] { 3, DateTime.parse("2018-01-01T00:14:03+00:00"), DateTime.parse("2017-12-31T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 4, DateTime.parse("2018-12-31T23:40:50+01:00"), DateTime.parse("2018-12-31T00:00:00+01:00"), null },
      new Object[] { 5, DateTime.parse("2018-12-31T23:40:50+00:00"), DateTime.parse("2019-01-01T00:00:00+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfDayForDateTimeParameters")
  void shouldGetCorrectEndOfDayForDateTime(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = zone == null
      ? DateTimeUtil.atEndOfDay(from)
      : DateTimeUtil.atEndOfDay(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfDayForDateTimeParameters() {
    return new Object[] {
      new Object[] { 0, DateTime.parse("2018-03-01T02:28:19-05:00"), DateTime.parse("2018-03-01T23:59:59-05:00"), null },
      new Object[] { 1, DateTime.parse("2018-02-28T21:28:19+00:00"), DateTime.parse("2018-02-28T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, DateTime.parse("2018-01-01T00:14:03-05:00"), DateTime.parse("2018-01-01T23:59:59-05:00"), null },
      new Object[] { 3, DateTime.parse("2018-01-01T00:14:03+00:00"), DateTime.parse("2017-12-31T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 4, DateTime.parse("2018-12-31T23:40:50+01:00"), DateTime.parse("2018-12-31T23:59:59+01:00"), null },
      new Object[] { 5, DateTime.parse("2018-12-31T23:40:50+00:00"), DateTime.parse("2019-01-01T23:59:59+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectStartOfDayForJodaLocalDateParameters")
  void shouldGetCorrectStartOfDayForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atStartOfDay(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectStartOfDayForJodaLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, org.joda.time.LocalDate.parse("2018-02-28"), DateTime.parse("2018-02-28T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 1, org.joda.time.LocalDate.parse("2018-01-01"), DateTime.parse("2018-01-01T00:00:00-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, org.joda.time.LocalDate.parse("2018-12-31"), DateTime.parse("2018-12-31T00:00:00+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

  @ParameterizedTest
  @MethodSource("shouldGetCorrectEndOfDayForJodaLocalDateParameters")
  void shouldGetCorrectEndOfDayForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atEndOfDay(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  private static Object[] shouldGetCorrectEndOfDayForJodaLocalDateParameters() {
    return new Object[] {
      new Object[] { 0, org.joda.time.LocalDate.parse("2018-02-28"), DateTime.parse("2018-02-28T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 1, org.joda.time.LocalDate.parse("2018-01-01"), DateTime.parse("2018-01-01T23:59:59-05:00"), DateTimeZone.forID("America/New_York") },
      new Object[] { 2, org.joda.time.LocalDate.parse("2018-12-31"), DateTime.parse("2018-12-31T23:59:59+01:00"), DateTimeZone.forID("Europe/Warsaw") }
    };
  }

}
