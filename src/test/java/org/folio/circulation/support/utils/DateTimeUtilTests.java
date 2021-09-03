package org.folio.circulation.support.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DateTimeUtilTests {
  private static final String FORMATTED_DATE_NOW = "2020-10-20";
  private static final String FORMATTED_TIME_NOW = "10:20:10.000";
  private static final String FORMATTED_DATE_TIME_NOW = "2020-10-20T10:20:10.000Z";
  private static final String FORMATTED_LOCAL_DATE_TIME_NOW = "2020-10-20T10:20:10";

  @BeforeEach
  public void beforeEach() {
    ClockUtil.setClock(Clock.fixed(Instant.parse(FORMATTED_DATE_TIME_NOW), ZoneOffset.UTC));
  }

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
    final ZonedDateTime result = DateTimeUtil.normalizeDateTime(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "1, null, " + FORMATTED_DATE_TIME_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedOffsetDateTime(int id, OffsetDateTime from, OffsetDateTime expected) {
    final OffsetDateTime result = DateTimeUtil.normalizeDateTime(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10T10:10:10, 2010-10-10T10:10:10",
    "1, null, " + FORMATTED_LOCAL_DATE_TIME_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedLocalDateTime(int id, LocalDateTime from, LocalDateTime expected) {
    final LocalDateTime result = DateTimeUtil.normalizeDateTime(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10, 2010-10-10",
    "1, null, " + FORMATTED_DATE_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedLocalDate(int id, LocalDate from, LocalDate expected) {
    final LocalDate result = DateTimeUtil.normalizeDate(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 10:10:10, 10:10:10",
    "1, null, " + FORMATTED_TIME_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedLocalTime(int id, LocalTime from, LocalTime expected) {
    final LocalTime result = DateTimeUtil.normalizeTime(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "1, null, " + FORMATTED_DATE_TIME_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedInstant(int id, ZonedDateTime from, ZonedDateTime expected) {
    final Instant fromInstant = from == null
      ? null
      : from.toInstant();
    final Instant expectedInstant = expected.toInstant();

    final Instant result = DateTimeUtil.normalizeDateTime(fromInstant);

    assertEquals(expectedInstant.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "1, null, " + FORMATTED_DATE_TIME_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedJodaDateTime(int id, DateTime from, DateTime expected) {
    final DateTime result = DateTimeUtil.normalizeDateTime(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10T10:10:10, 2010-10-10T10:10:10",
    "1, null, " + FORMATTED_LOCAL_DATE_TIME_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedJodaLocalDateTime(int id, org.joda.time.LocalDateTime from, org.joda.time.LocalDateTime expected) {
    final org.joda.time.LocalDateTime result = DateTimeUtil.normalizeDateTime(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10, 2010-10-10",
    "1, null, " + FORMATTED_DATE_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedJodaLocalDate(int id, org.joda.time.LocalDate from, org.joda.time.LocalDate expected) {
    final org.joda.time.LocalDate result = DateTimeUtil.normalizeDate(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10, 2010-10-10",
    "1, null, " + FORMATTED_DATE_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedJodaDate(int id, org.joda.time.LocalDate from, org.joda.time.LocalDate expected) {
    final org.joda.time.LocalDate result = DateTimeUtil.normalizeDate(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z",
    "1, null, " + FORMATTED_DATE_TIME_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedJodaInstant(int id, DateTime from, DateTime expected) {
    final org.joda.time.Instant fromInstant = from == null
      ? null
      : from.toInstant();
    final org.joda.time.Instant expectedInstant = expected.toInstant();

    final org.joda.time.Instant result = DateTimeUtil.normalizeDateTime(fromInstant);

    assertEquals(expectedInstant.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 10:10:10, 10:10:10",
    "1, null, " + FORMATTED_TIME_NOW
  }, nullValues = {"null"})
  void shouldGetNormalizedJodaLocalTime(int id, org.joda.time.LocalTime from, org.joda.time.LocalTime expected) {
    final org.joda.time.LocalTime result = DateTimeUtil.normalizeTime(from);

    assertEquals(expected.toString(), result.toString(), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, America/New_York, America/New_York",
    "1, Europe/Warsaw, Europe/Warsaw",
    "2, null, Z"
  }, nullValues = {"null"})
  void shouldGetNormalizedZoneId(int id, ZoneId from, ZoneId expected) {
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
    final ZonedDateTime datePast = ClockUtil.getZonedDateTime().minusDays(2);
    final ZonedDateTime datePresent = ClockUtil.getZonedDateTime();
    final ZonedDateTime dateFuture = ClockUtil.getZonedDateTime().plusDays(2);

    final ZonedDateTime result = DateTimeUtil.mostRecentDate(datePresent, dateFuture, datePast);

    assertEquals(dateFuture, result);
  }

  @Test
  void shouldGetMostRecentJodaDateTime() {
    final DateTime datePast = ClockUtil.getDateTime().minusDays(2);
    final DateTime datePresent = ClockUtil.getDateTime();
    final DateTime dateFuture = ClockUtil.getDateTime().plusDays(2);

    final DateTime result = DateTimeUtil.mostRecentDate(datePresent, dateFuture, datePast);

    assertEquals(dateFuture, result);
  }

  @Test
  void shouldGetToZonedDateTime() {
    final DateTime jodaDate = ClockUtil.getDateTime();
    final ZonedDateTime result = DateTimeUtil.toZonedDateTime(jodaDate);

    assertEquals(ClockUtil.getDateTime().toInstant().getMillis(), result.toInstant().toEpochMilli());
  }

  @Test
  void shouldGetToOffsetDateTime() {
    final DateTime jodaDate = ClockUtil.getDateTime();
    final OffsetDateTime result = DateTimeUtil.toOffsetDateTime(jodaDate);

    assertEquals(ClockUtil.getDateTime().toInstant().getMillis(), result.toInstant().toEpochMilli());
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 2010-10-10T10:10:10.000Z, null, true",
    "2, null, 2010-10-10T10:10:10.000Z, false",
    "3, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z, false",
    "4, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.001Z, true",
    "5, 2010-10-10T10:10:10.001Z, 2010-10-10T10:10:10.000Z, false",
  }, nullValues = {"null"})
  void shouldCompareIsZonedDateTimeBeforeMillis(int id, ZonedDateTime left, ZonedDateTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isBeforeMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 2010-10-10T10:10:10.000, null, true",
    "2, null, 2010-10-10T10:10:10.000, false",
    "3, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.000, false",
    "4, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.001, true",
    "5, 2010-10-10T10:10:10.001, 2010-10-10T10:10:10.000, false",
  }, nullValues = {"null"})
  void shouldCompareIsLocalDateTimeBeforeMillis(int id, LocalDateTime left, LocalDateTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isBeforeMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 10:10:10.000, null, true",
    "2, null, 10:10:10.000, false",
    "3, 10:10:10.000, 10:10:10.000, false",
    "4, 10:10:10.000, 10:10:10.001, true",
    "5, 10:10:10.001, 10:10:10.000, false",
  }, nullValues = {"null"})
  void shouldCompareIsLocalTimeBeforeMillis(int id, LocalTime left, LocalTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isBeforeMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 2010-10-10T10:10:10.000Z, null, true",
    "2, null, 2010-10-10T10:10:10.000Z, false",
    "3, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z, false",
    "4, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.001Z, true",
    "5, 2010-10-10T10:10:10.001Z, 2010-10-10T10:10:10.000Z, false",
  }, nullValues = {"null"})
  void shouldCompareIsJodaDateTimeBeforeMillis(int id, DateTime left, DateTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isBeforeMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 2010-10-10T10:10:10.000, null, true",
    "2, null, 2010-10-10T10:10:10.000, false",
    "3, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.000, false",
    "4, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.001, true",
    "5, 2010-10-10T10:10:10.001, 2010-10-10T10:10:10.000, false",
  }, nullValues = {"null"})
  void shouldCompareIsJodaLocalDateTimeBeforeMillis(int id, org.joda.time.LocalDateTime left, org.joda.time.LocalDateTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isBeforeMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 10:10:10.000, null, true",
    "2, null, 10:10:10.000, false",
    "3, 10:10:10.000, 10:10:10.000, false",
    "4, 10:10:10.000, 10:10:10.001, true",
    "5, 10:10:10.001, 10:10:10.000, false",
  }, nullValues = {"null"})
  void shouldCompareIsJodaLocalTimeBeforeMillis(int id, org.joda.time.LocalTime left, org.joda.time.LocalTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isBeforeMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 2010-10-10T10:10:10.000Z, null, false",
    "2, null, 2010-10-10T10:10:10.000Z, true",
    "3, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z, false",
    "4, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.001Z, false",
    "5, 2010-10-10T10:10:10.001Z, 2010-10-10T10:10:10.000Z, true",
  }, nullValues = {"null"})
  void shouldCompareIsZonedDateTimeAfterMillis(int id, ZonedDateTime left, ZonedDateTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isAfterMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 2010-10-10T10:10:10.000, null, false",
    "2, null, 2010-10-10T10:10:10.000, true",
    "3, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.000, false",
    "4, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.001, false",
    "5, 2010-10-10T10:10:10.001, 2010-10-10T10:10:10.000, true",
  }, nullValues = {"null"})
  void shouldCompareIsLocalDateTimeAfterMillis(int id, LocalDateTime left, LocalDateTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isAfterMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 10:10:10.000, null, false",
    "2, null, 10:10:10.000, true",
    "3, 10:10:10.000, 10:10:10.000, false",
    "4, 10:10:10.000, 10:10:10.001, false",
    "5, 10:10:10.001, 10:10:10.000, true",
  }, nullValues = {"null"})
  void shouldCompareIsLocalTimeAfterMillis(int id, LocalTime left, LocalTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isAfterMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 2010-10-10T10:10:10.000Z, null, false",
    "2, null, 2010-10-10T10:10:10.000Z, true",
    "3, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z, false",
    "4, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.001Z, false",
    "5, 2010-10-10T10:10:10.001Z, 2010-10-10T10:10:10.000Z, true",
  }, nullValues = {"null"})
  void shouldCompareIsJodaDateTimeAfterMillis(int id, DateTime left, DateTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isAfterMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 2010-10-10T10:10:10.000, null, false",
    "2, null, 2010-10-10T10:10:10.000, true",
    "3, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.000, false",
    "4, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.001, false",
    "5, 2010-10-10T10:10:10.001, 2010-10-10T10:10:10.000, true",
  }, nullValues = {"null"})
  void shouldCompareIsJodaLocalDateTimeAfterMillis(int id, org.joda.time.LocalDateTime left, org.joda.time.LocalDateTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isAfterMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, false",
    "1, 10:10:10.000, null, false",
    "2, null, 10:10:10.000, true",
    "3, 10:10:10.000, 10:10:10.000, false",
    "4, 10:10:10.000, 10:10:10.001, false",
    "5, 10:10:10.001, 10:10:10.000, true",
  }, nullValues = {"null"})
  void shouldCompareIsJodaLocalDateTimeAfterMillis(int id, org.joda.time.LocalTime left, org.joda.time.LocalTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isAfterMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, true",
    "1, 2010-10-10T10:10:10.000Z, null, false",
    "2, null, 2010-10-10T10:10:10.000Z, false",
    "3, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z, true",
    "4, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.001Z, false",
    "5, 2010-10-10T10:10:10.001Z, 2010-10-10T10:10:10.000Z, false",
  }, nullValues = {"null"})
  void shouldCompareIsZonedDateTimeSameMillis(int id, ZonedDateTime left, ZonedDateTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isSameMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, true",
    "1, 2010-10-10T10:10:10.000, null, false",
    "2, null, 2010-10-10T10:10:10.000, false",
    "3, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.000, true",
    "4, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.001, false",
    "5, 2010-10-10T10:10:10.001, 2010-10-10T10:10:10.000, false",
  }, nullValues = {"null"})
  void shouldCompareIsLocalDateTimeSameMillis(int id, LocalDateTime left, LocalDateTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isSameMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, true",
    "1, 10:10:10.000, null, false",
    "2, null, 10:10:10.000, false",
    "3, 10:10:10.000, 10:10:10.000, true",
    "4, 10:10:10.000, 10:10:10.001, false",
    "5, 10:10:10.001, 10:10:10.000, false",
  }, nullValues = {"null"})
  void shouldCompareIsLocalTimeSameMillis(int id, LocalTime left, LocalTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isSameMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, true",
    "1, 2010-10-10T10:10:10.000Z, null, false",
    "2, null, 2010-10-10T10:10:10.000Z, false",
    "3, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z, true",
    "4, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.001Z, false",
    "5, 2010-10-10T10:10:10.001Z, 2010-10-10T10:10:10.000Z, false",
  }, nullValues = {"null"})
  void shouldCompareIsJodaDateTimeSameMillis(int id, DateTime left, DateTime right, boolean expected) {
    assertEquals(expected, DateTimeUtil.isSameMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, true",
    "1, 2010-10-10T10:10:10.000Z, null, false",
    "2, null, 2010-10-10T10:10:10.000Z, false",
    "3, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z, true",
    "4, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.001Z, false",
    "5, 2010-10-10T10:10:10.001Z, 2010-10-10T10:10:10.000Z, false",
  }, nullValues = {"null"})
  void shouldCompareIsInstantSameMillis(int id, ZonedDateTime left, ZonedDateTime right, boolean expected) {
    final Instant leftInstant = left == null
      ? null
      : left.toInstant();

    final Instant rightInstant = right == null
      ? null
      : right.toInstant();

    assertEquals(expected, DateTimeUtil.isSameMillis(leftInstant, rightInstant), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, true",
    "1, 2010-10-10T10:10:10.000Z, null, false",
    "2, null, 2010-10-10T10:10:10.000Z, false",
    "3, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z, true",
    "4, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.001Z, false",
    "5, 2010-10-10T10:10:10.001Z, 2010-10-10T10:10:10.000Z, false",
  }, nullValues = {"null"})
  void shouldCompareIsJodaInstantSameMillis(int id, DateTime left, DateTime right, boolean expected) {
    final org.joda.time.Instant leftInstant = left == null
      ? null
      : left.toInstant();

    final org.joda.time.Instant rightInstant = right == null
      ? null
      : right.toInstant();

    assertEquals(expected, DateTimeUtil.isSameMillis(leftInstant, rightInstant), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null, false",
    "1, null, 2010-05-05T10:05:05.000Z, 2010-12-12T10:12:12.000Z, false",
    "2, 2010-10-10T10:10:10.000Z, null, 2010-12-12T10:12:12.000Z, false",
    "3, 2010-10-10T10:10:10.000Z, 2010-05-05T10:05:05.000Z, null, true",
    "4, null, null, 2010-12-12T10:12:12.000Z, false",
    "5, 2010-10-10T10:10:10.000Z, null, null, false",
    "6, 2010-10-10T10:10:10.000Z, 2010-05-05T10:05:05.000Z, 2010-12-12T10:12:12.000Z, true",
    "7, 2010-01-01T00:00:00.000Z, 2010-05-05T10:05:05.000Z, 2010-12-12T10:12:12.000Z, false",
    "8, 2010-12-12T12:00:00.000Z, 2010-05-05T10:05:05.000Z, 2010-12-12T10:12:12.000Z, false",
  }, nullValues = {"null"})
  void shouldCompareIsZonedDateTimeWithinMillis(int id, ZonedDateTime date, ZonedDateTime first, ZonedDateTime last, boolean expected) {
    assertEquals(expected, DateTimeUtil.isWithinMillis(date, first, last), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null, false",
    "1, null, 2010-05-05T10:05:05.000, 2010-12-12T10:12:12.000, false",
    "2, 2010-10-10T10:10:10.000, null, 2010-12-12T10:12:12.000, false",
    "3, 2010-10-10T10:10:10.000, 2010-05-05T10:05:05.000, null, true",
    "4, null, null, 2010-12-12T10:12:12.000, false",
    "5, 2010-10-10T10:10:10.000, null, null, false",
    "6, 2010-10-10T10:10:10.000, 2010-05-05T10:05:05.000, 2010-12-12T10:12:12.000, true",
    "7, 2010-01-01T00:00:00.000, 2010-05-05T10:05:05.000, 2010-12-12T10:12:12.000, false",
    "8, 2010-12-12T12:00:00.000, 2010-05-05T10:05:05.000, 2010-12-12T10:12:12.000, false",
  }, nullValues = {"null"})
  void shouldCompareIsLocalDateTimeWithinMillis(int id, LocalDateTime date, LocalDateTime first, LocalDateTime last, boolean expected) {
    assertEquals(expected, DateTimeUtil.isWithinMillis(date, first, last), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null, false",
    "1, null, 10:05:05.000, 10:12:12.000, false",
    "2, 10:10:10.000, null, 10:12:12.000, false",
    "3, 10:10:10.000, 10:05:05.000, null, true",
    "4, null, null, 10:12:12.000, false",
    "5, 10:10:10.000, null, null, false",
    "6, 10:10:10.000, 10:05:05.000, 10:12:12.000, true",
    "7, 00:00:00.000, 10:05:05.000, 10:12:12.000, false",
    "8, 12:00:00.000, 10:05:05.000, 10:12:12.000, false",
  }, nullValues = {"null"})
  void shouldCompareIsLocalTimeWithinMillis(int id, LocalTime date, LocalTime first, LocalTime last, boolean expected) {
    assertEquals(expected, DateTimeUtil.isWithinMillis(date, first, last), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null, false",
    "1, null, 2010-05-05T10:05:05.000Z, 2010-12-12T10:12:12.000Z, false",
    "2, 2010-10-10T10:10:10.000Z, null, 2010-12-12T10:12:12.000Z, false",
    "3, 2010-10-10T10:10:10.000Z, 2010-05-05T10:05:05.000Z, null, true",
    "4, null, null, 2010-12-12T10:12:12.000Z, false",
    "5, 2010-10-10T10:10:10.000Z, null, null, false",
    "6, 2010-10-10T10:10:10.000Z, 2010-05-05T10:05:05.000Z, 2010-12-12T10:12:12.000Z, true",
    "7, 2010-01-01T00:00:00.000Z, 2010-05-05T10:05:05.000Z, 2010-12-12T10:12:12.000Z, false",
    "8, 2010-12-12T12:00:00.000Z, 2010-05-05T10:05:05.000Z, 2010-12-12T10:12:12.000Z, false",
  }, nullValues = {"null"})
  void shouldCompareIsJodaZonedDateTimeWithinMillis(int id, DateTime date, DateTime first, DateTime last, boolean expected) {
    assertEquals(expected, DateTimeUtil.isWithinMillis(date, first, last), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null, false",
    "1, null, 2010-05-05T10:05:05.000, 2010-12-12T10:12:12.000, false",
    "2, 2010-10-10T10:10:10.000, null, 2010-12-12T10:12:12.000, false",
    "3, 2010-10-10T10:10:10.000, 2010-05-05T10:05:05.000, null, true",
    "4, null, null, 2010-12-12T10:12:12.000, false",
    "5, 2010-10-10T10:10:10.000, null, null, false",
    "6, 2010-10-10T10:10:10.000, 2010-05-05T10:05:05.000, 2010-12-12T10:12:12.000, true",
    "7, 2010-01-01T00:00:00.000, 2010-05-05T10:05:05.000, 2010-12-12T10:12:12.000, false",
    "8, 2010-12-12T12:00:00.000, 2010-05-05T10:05:05.000, 2010-12-12T10:12:12.000, false",
  }, nullValues = {"null"})
  void shouldCompareIsJodaLocalDateTimeWithinMillis(int id, org.joda.time.LocalDateTime date, org.joda.time.LocalDateTime first, org.joda.time.LocalDateTime last, boolean expected) {
    assertEquals(expected, DateTimeUtil.isWithinMillis(date, first, last), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, null, false",
    "1, null, 10:05:05.000, 10:12:12.000, false",
    "2, 10:10:10.000, null, 10:12:12.000, false",
    "3, 10:10:10.000, 10:05:05.000, null, true",
    "4, null, null, 10:12:12.000, false",
    "5, 10:10:10.000, null, null, false",
    "6, 10:10:10.000, 10:05:05.000, 10:12:12.000, true",
    "7, 00:00:00.000, 10:05:05.000, 10:12:12.000, false",
    "8, 12:00:00.000, 10:05:05.000, 10:12:12.000, false",
  }, nullValues = {"null"})
  void shouldCompareIsJodaLocalTimeWithinMillis(int id, org.joda.time.LocalTime date, org.joda.time.LocalTime first, org.joda.time.LocalTime last, boolean expected) {
    assertEquals(expected, DateTimeUtil.isWithinMillis(date, first, last), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, 0",
    "1, 2010-10-10T10:10:10.000Z, null, -1",
    "2, null, 2010-10-10T10:10:10.000Z, 1",
    "3, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z, 0",
    "4, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.001Z, -1",
    "5, 2010-10-10T10:10:10.001Z, 2010-10-10T10:10:10.000Z, 1",
  }, nullValues = {"null"})
  void shouldCompareIsZonedDateTimeCompareToMillis(int id, ZonedDateTime left, ZonedDateTime right, int expected) {
    assertEquals(expected, DateTimeUtil.compareToMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, 0",
    "1, 10:10:10.000, null, -1",
    "2, null, 10:10:10.000, 1",
    "3, 10:10:10.000, 10:10:10.000, 0",
    "4, 10:10:10.000, 10:10:10.001, -1",
    "5, 10:10:10.001, 10:10:10.000, 1",
  }, nullValues = {"null"})
  void shouldCompareIsLocalTimeCompareToMillis(int id, LocalTime left, LocalTime right, int expected) {
    assertEquals(expected, DateTimeUtil.compareToMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, 0",
    "1, 2010-10-10T10:10:10.000Z, null, -1",
    "2, null, 2010-10-10T10:10:10.000Z, 1",
    "3, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z, 0",
    "4, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.001Z, -1",
    "5, 2010-10-10T10:10:10.001Z, 2010-10-10T10:10:10.000Z, 1",
  }, nullValues = {"null"})
  void shouldCompareIsJodaDateTimeCompareToMillis(int id, DateTime left, DateTime right, int expected) {
    assertEquals(expected, DateTimeUtil.compareToMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, 0",
    "1, 10:10:10.000, null, -1",
    "2, null, 10:10:10.000, 1",
    "3, 10:10:10.000, 10:10:10.000, 0",
    "4, 10:10:10.000, 10:10:10.001, -1",
    "5, 10:10:10.001, 10:10:10.000, 1",
  }, nullValues = {"null"})
  void shouldCompareIsJodaLocalTimeCompareToMillis(int id, org.joda.time.LocalTime left, org.joda.time.LocalTime right, int expected) {
    assertEquals(expected, DateTimeUtil.compareToMillis(left, right), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, 0",
    "1, 1970-01-01T00:03:20.000Z, null, 0",
    "2, null, 1970-01-01T00:03:20.000Z, 100000",
    "3, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.000Z, 0",
    "4, 2010-10-10T10:10:10.000Z, 2010-10-10T10:10:10.001Z, 1",
    "5, 2010-10-10T10:10:10.001Z, 2010-10-10T10:10:10.000Z, 0",
  }, nullValues = {"null"})
  void shouldGetMillisBetweenZonedDateTimes(int id, ZonedDateTime begin, ZonedDateTime end, int expected) {
    ClockUtil.setClock(Clock.fixed(Instant.ofEpochMilli(100000), ZoneOffset.UTC));

    assertEquals(expected, DateTimeUtil.millisBetween(begin, end), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, null, null, 0",
    "1, 1970-01-01T00:03:20.000, null, 0",
    "2, null, 1970-01-01T00:03:20.000, 100000",
    "3, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.000, 0",
    "4, 2010-10-10T10:10:10.000, 2010-10-10T10:10:10.001, 1",
    "5, 2010-10-10T10:10:10.001, 2010-10-10T10:10:10.000, 0",
  }, nullValues = {"null"})
  void shouldGetMillisBetweenLocalDateTimes(int id, LocalDateTime begin, LocalDateTime end, int expected) {
    ClockUtil.setClock(Clock.fixed(Instant.ofEpochMilli(100000), ZoneOffset.UTC));

    assertEquals(expected, DateTimeUtil.millisBetween(begin, end), "For test " + id);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0, 0, 0, 0",
    "1, 0, 1, 1",
    "2, 1, 0, 0",
  }, nullValues = {"null"})
  void shouldGetMillisBetweenMilliseconds(int id, long begin, long end, int expected) {
    ClockUtil.setClock(Clock.fixed(Instant.ofEpochMilli(100000), ZoneOffset.UTC));

    assertEquals(expected, DateTimeUtil.millisBetween(begin, end), "For test " + id);
  }

}
