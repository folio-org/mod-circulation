package org.folio.circulation.support.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DateTimeUtilTests {
  private static final String FORMATTED_DATE_TIME_NOW = "2020-10-20T10:10:10.000Z";

  @AfterEach
  public void afterEach() {
    // The clock must be reset after each test.
    ClockUtil.setDefaultClock();
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "1, null, " + FORMATTED_DATE_TIME_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected) {
    setFixedClock();

    final ZonedDateTime result = DateTimeUtil.normalizeDateTime(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "1, null, " + FORMATTED_DATE_TIME_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedOffsetDateTime(int id, OffsetDateTime from, OffsetDateTime expected) {
    setFixedClock();

    final OffsetDateTime result = DateTimeUtil.normalizeDateTime(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10T10:10:10, 2010-10-10T10:10:10",
    "1, null, 2020-10-20T10:10:10"
  }, nullValues = {"null"})
  void shouldGetNormalizedLocalDateTime(int id, LocalDateTime from, LocalDateTime expected) {
    setFixedClock();

    final LocalDateTime result = DateTimeUtil.normalizeDateTime(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10, 2010-10-10",
    "1, null, 2020-10-20"
  }, nullValues = {"null"})
  void shouldGetNormalizedLocalDate(int id, LocalDate from, LocalDate expected) {
    setFixedClock();

    final LocalDate result = DateTimeUtil.normalizeDate(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "1, null, " + FORMATTED_DATE_TIME_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedJodaDateTime(int id, DateTime from, DateTime expected) {
    setFixedClock();

    final DateTime result = DateTimeUtil.normalizeDateTime(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10, 2010-10-10",
    "1, null, 2020-10-20"
  }, nullValues = {"null"})
  void shouldGetNormalizedJodaDate(int id, org.joda.time.LocalDate from, org.joda.time.LocalDate expected) {
    setFixedClock();

    final org.joda.time.LocalDate result = DateTimeUtil.normalizeDate(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, America/New_York, America/New_York",
    "1, Europe/Warsaw, Europe/Warsaw",
    "2, null, Z"
  }, nullValues = {"null"})
  void shouldGetNormalizedZoneId(int id, ZoneId from, ZoneId expected) {
    setFixedClock();

    final ZoneId result = DateTimeUtil.normalizeZone(from);

    assertEquals(expected, result, "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, America/New_York, America/New_York",
    "1, Europe/Warsaw, Europe/Warsaw",
    "2, null, Z"
  }, nullValues = {"null"})
  void shouldGetNormalizedZoneOffset(int id, ZoneId from, ZoneId expected) {
    setFixedClock();

    final ZoneOffset fromOffset = from == null
      ? null
      : from.getRules().getOffset(Instant.EPOCH);

    final ZoneOffset expectedOffset = expected.getRules().getOffset(Instant.EPOCH);
    final ZoneOffset result = DateTimeUtil.normalizeZone(fromOffset);

    assertEquals(expectedOffset, result, "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, America/New_York, America/New_York",
    "1, Europe/Warsaw, Europe/Warsaw",
    "2, null, UTC"
  }, nullValues = {"null"})
  void shouldGetNormalizedJodaZone(int id, DateTimeZone from, DateTimeZone expected) {
    setFixedClock();

    final DateTimeZone result = DateTimeUtil.normalizeZone(from);

    assertEquals(expected, result, "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-01-01T00:00:00-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-01-01T00:00:00-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-01-01T00:00:00+01:00"
  })
  void shouldGetCorrectStartOfYearForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected) {
    final ZonedDateTime result = DateTimeUtil.atStartOfYear(from);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-01-01T00:00:00-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-01-01T00:00:00-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-01-01T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfYearForZonedDateTimeWithZone(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atStartOfYear(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-12-31T23:59:59-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-12-31T23:59:59-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-12-31T23:59:59+01:00"
  })
  void shouldGetCorrectEndOfYearForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected) {
    final ZonedDateTime result = DateTimeUtil.atEndOfYear(from);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-12-31T23:59:59-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-12-31T23:59:59-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-12-31T23:59:59+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectEndOfYearForZonedDateTimeWithZone(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atEndOfYear(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-01-01T00:00:00-05:00, America/New_York",
    "1, 2018-01-01, 2018-01-01T00:00:00-05:00, America/New_York",
    "2, 2018-12-31, 2018-01-01T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfYearForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atStartOfYear(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-12-31T23:59:59-05:00, America/New_York",
    "1, 2018-01-01, 2018-12-31T23:59:59-05:00, America/New_York",
    "2, 2018-12-31, 2018-12-31T23:59:59+01:00, Europe/Warsaw",
  })
  void shouldGetCorrectEndOfYearForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atEndOfYear(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-01-01T00:00:00-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-01-01T00:00:00-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-01-01T00:00:00+01:00"
  })
  void shouldGetCorrectStartOfYearForJodaDateTime(int id, DateTime from, DateTime expected) {
    final DateTime result = DateTimeUtil.atStartOfYear(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-01-01T00:00:00-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-01-01T00:00:00-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-01-01T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfYearForJodaDateTimeWithZone(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atStartOfYear(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-12-31T23:59:59-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-12-31T23:59:59-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-12-31T23:59:59+01:00"
  })
  void shouldGetCorrectEndOfYearForJodaDateTime(int id, DateTime from, DateTime expected) {
    final DateTime result = DateTimeUtil.atEndOfYear(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-12-31T23:59:59-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-12-31T23:59:59-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-12-31T23:59:59+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectEndOfYearForJodaDateTimeWithZone(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atEndOfYear(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-01-01T00:00:00-05:00, America/New_York",
    "1, 2018-01-01, 2018-01-01T00:00:00-05:00, America/New_York",
    "2, 2018-12-31, 2018-01-01T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfYearForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atStartOfYear(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-12-31T23:59:59-05:00, America/New_York",
    "1, 2018-01-01, 2018-12-31T23:59:59-05:00, America/New_York",
    "2, 2018-12-31, 2018-12-31T23:59:59+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectEndOfYearForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atEndOfYear(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-03-01T00:00:00-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-01-01T00:00:00-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-12-01T00:00:00+01:00"
  })
  void shouldGetCorrectStartOfMonthForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected) {
    final ZonedDateTime result = DateTimeUtil.atStartOfMonth(from);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-02-01T00:00:00-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-12-01T00:00:00-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-01-01T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfMonthForZonedDateTimeWithZone(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atStartOfMonth(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-03-31T23:59:59-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-01-31T23:59:59-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-12-31T23:59:59+01:00"
  })
  void shouldGetCorrectEndOfMonthForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected) {
    final ZonedDateTime result = DateTimeUtil.atEndOfMonth(from);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-02-28T23:59:59-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-12-31T23:59:59-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-01-31T23:59:59+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectEndOfMonthForZonedDateTimeWithZone(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atEndOfMonth(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-02-01T00:00:00-05:00, America/New_York",
    "1, 2018-01-01, 2018-01-01T00:00:00-05:00, America/New_York",
    "2, 2018-12-31, 2018-12-01T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfMonthForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atStartOfMonth(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-02-28T23:59:59-05:00, America/New_York",
    "1, 2018-01-01, 2018-01-31T23:59:59-05:00, America/New_York",
    "2, 2018-12-31, 2018-12-31T23:59:59+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectEndOfMonthForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atEndOfMonth(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-03-01T00:00:00-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-01-01T00:00:00-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-12-01T00:00:00+01:00"
  })
  void shouldGetCorrectStartOfMonthForJodaDateTime(int id, DateTime from, DateTime expected) {
    final DateTime result = DateTimeUtil.atStartOfMonth(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-02-01T00:00:00-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-12-01T00:00:00-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-01-01T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfMonthForJodaDateTimeWithZone(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atStartOfMonth(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-03-31T23:59:59-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-01-31T23:59:59-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-12-31T23:59:59+01:00"
  })
  void shouldGetCorrectEndOfMonthForJodaDateTime(int id, DateTime from, DateTime expected) {
    final DateTime result = DateTimeUtil.atEndOfMonth(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-02-28T23:59:59-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-12-31T23:59:59-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-01-31T23:59:59+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectEndOfMonthForJodaDateTimeWithZone(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atEndOfMonth(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-02-01T00:00:00-05:00, America/New_York",
    "1, 2018-01-01, 2018-01-01T00:00:00-05:00, America/New_York",
    "2, 2018-12-31, 2018-12-01T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfMonthForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atStartOfMonth(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-02-28T23:59:59-05:00, America/New_York",
    "1, 2018-01-01, 2018-01-31T23:59:59-05:00, America/New_York",
    "2, 2018-12-31, 2018-12-31T23:59:59+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectEndOfMonthForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atEndOfMonth(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-03-01T00:00:00-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-01-01T00:00:00-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-12-31T00:00:00+01:00"
  })
  void shouldGetCorrectStartOfDayForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected) {
    final ZonedDateTime result = DateTimeUtil.atStartOfDay(from);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-02-28T00:00:00-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-12-31T00:00:00-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-01-01T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfDayForZonedDateTimeWithZone(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atStartOfDay(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-03-01T23:59:59-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-01-01T23:59:59-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-12-31T23:59:59+01:00"
  })
  void shouldGetCorrectEndOfDayForZonedDateTime(int id, ZonedDateTime from, ZonedDateTime expected) {
    final ZonedDateTime result = DateTimeUtil.atEndOfDay(from);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-02-28T23:59:59-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-12-31T23:59:59-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-01-01T23:59:59+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectEndOfDayForZonedDateTimeWithZone(int id, ZonedDateTime from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atEndOfDay(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-02-28T00:00:00-05:00, America/New_York",
    "1, 2018-01-01, 2018-01-01T00:00:00-05:00, America/New_York",
    "2, 2018-12-31, 2018-12-31T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfDayForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atStartOfDay(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-02-28T23:59:59-05:00, America/New_York",
    "1, 2018-01-01, 2018-01-01T23:59:59-05:00, America/New_York",
    "2, 2018-12-31, 2018-12-31T23:59:59+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectEndOfDayForLocalDate(int id, LocalDate from, ZonedDateTime expected, ZoneId zone) {
    final ZonedDateTime result = DateTimeUtil.atEndOfDay(from, zone);

    assertEquals(expected.format(DateTimeFormatter.ISO_DATE), result.format(DateTimeFormatter.ISO_DATE), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-03-01T00:00:00-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-01-01T00:00:00-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-12-31T00:00:00+01:00"
  })
  void shouldGetCorrectStartOfDayForJodaDateTime(int id, DateTime from, DateTime expected) {
    final DateTime result = DateTimeUtil.atStartOfDay(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-02-28T00:00:00-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-12-31T00:00:00-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-01-01T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfDayForJodaDateTimeWithZone(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atStartOfDay(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-03-01T02:28:19-05:00, 2018-03-01T23:59:59-05:00",
    "1, 2018-01-01T00:14:03-05:00, 2018-01-01T23:59:59-05:00",
    "2, 2018-12-31T23:40:50+01:00, 2018-12-31T23:59:59+01:00"
  })
  void shouldGetCorrectEndOfDayForJodaDateTime(int id, DateTime from, DateTime expected) {
    final DateTime result = DateTimeUtil.atEndOfDay(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28T21:28:19+00:00, 2018-02-28T23:59:59-05:00, America/New_York",
    "1, 2018-01-01T00:14:03+00:00, 2017-12-31T23:59:59-05:00, America/New_York",
    "2, 2018-12-31T23:40:50+00:00, 2019-01-01T23:59:59+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectEndOfDayForJodaDateTimeWithZone(int id, DateTime from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atEndOfDay(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-02-28T00:00:00-05:00, America/New_York",
    "1, 2018-01-01, 2018-01-01T00:00:00-05:00, America/New_York",
    "2, 2018-12-31, 2018-12-31T00:00:00+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectStartOfDayForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atStartOfDay(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2018-02-28, 2018-02-28T23:59:59-05:00, America/New_York",
    "1, 2018-01-01, 2018-01-01T23:59:59-05:00, America/New_York",
    "2, 2018-12-31, 2018-12-31T23:59:59+01:00, Europe/Warsaw"
  })
  void shouldGetCorrectEndOfDayForJodaLocalDate(int id, org.joda.time.LocalDate from, DateTime expected, DateTimeZone zone) {
    final DateTime result = DateTimeUtil.atEndOfDay(from, zone);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @Test
  void shouldGetMostRecentZonedDateTime() {
    setFixedClock();

    final ZonedDateTime datePast = ClockUtil.getZonedDateTime().minusDays(2);
    final ZonedDateTime datePresent = ClockUtil.getZonedDateTime();
    final ZonedDateTime dateFuture = ClockUtil.getZonedDateTime().plusDays(2);

    final ZonedDateTime result = DateTimeUtil.mostRecentDate(datePresent, dateFuture, datePast);

    assertEquals(dateFuture, result);
  }

  @Test
  void shouldGetMostRecentJodaDateTime() {
    setFixedClock();

    final DateTime datePast = ClockUtil.getDateTime().minusDays(2);
    final DateTime datePresent = ClockUtil.getDateTime();
    final DateTime dateFuture = ClockUtil.getDateTime().plusDays(2);

    final DateTime result = DateTimeUtil.mostRecentDate(datePresent, dateFuture, datePast);

    assertEquals(dateFuture, result);
  }

  @Test
  void shouldGetToZonedDateTime() {
    setFixedClock();

    final DateTime jodaDate = ClockUtil.getDateTime();
    final ZonedDateTime result = DateTimeUtil.toZonedDateTime(jodaDate);

    assertEquals(ClockUtil.getDateTime().toInstant().getMillis(), result.toInstant().toEpochMilli());
  }

  @Test
  void shouldGetToOffsetDateTime() {
    setFixedClock();

    final DateTime jodaDate = ClockUtil.getDateTime();
    final OffsetDateTime result = DateTimeUtil.toOffsetDateTime(jodaDate);

    assertEquals(ClockUtil.getDateTime().toInstant().getMillis(), result.toInstant().toEpochMilli());
  }

  private void setFixedClock() {
    ClockUtil.setClock(Clock.fixed(Instant.parse(FORMATTED_DATE_TIME_NOW), ZoneOffset.UTC));
  }
}
