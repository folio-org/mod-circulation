package org.folio.circulation.support.utils;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static org.folio.circulation.support.utils.DateTimeUtil.defaultToNow;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A utility for common date time formatting and parsing.
 * <p>
 * Most parsing and formatting methods will normalize the dates and times passed.
 * The normalization will replace null values with effective now() results.
 * This preserves behavior of JodaTime.
 * There are Optional methods that instead return null when passed a null date and time.
 * <p>
 * The JavaTime formatter sometimes appends [America/New_York] in addition to
 * the -500 offset. This is non-conforming with the FOLIO standard.
 * To avoid this, custom formatters are used to guarantee the format is within
 * FOLIO standards.
 * <p>
 * The toString() methods in the JavaTime date and time classes will use the
 * non-FOLIO standard formatting. Use these methods rather than a toString() to
 * generate a date and time string.
 * <p>
 * JavaTime does not directly support parsing a set of formats.
 * The parsers provided will loop over multiple formats, handling the details.
 */
public class DateFormatUtil {
  private DateFormatUtil() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  /**
   * A variation of ISO_LOCAL_TIME down to minutes.
   */
  public static final DateTimeFormatter TIME_MINUTES;
  static {
    TIME_MINUTES = new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .appendValue(HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(MINUTE_OF_HOUR, 2)
      .toFormatter();
  }

  /**
   * A variation of ISO_LOCAL_TIME down to seconds.
   */
  public static final DateTimeFormatter TIME_SECONDS;
  static {
    TIME_SECONDS = new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .append(TIME_MINUTES)
      .appendLiteral(':')
      .appendValue(SECOND_OF_MINUTE, 2)
      .toFormatter();
  }

  /**
   * A variation of ISO_LOCAL_TIME down to milliseconds.
   */
  public static final DateTimeFormatter TIME;
  static {
    TIME = new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .append(TIME_SECONDS)
      .optionalStart()
      .appendFraction(MILLI_OF_SECOND, 3, 3, true)
      .parseLenient()
      .appendOffset("+HHMM", "Z")
      .parseStrict()
      .toFormatter();
  }

  /**
   * A variation of ISO_LOCAL_TIME down to nanoseconds.
   */
  public static final DateTimeFormatter TIME_NANOSECONDS;
  static {
    TIME_NANOSECONDS = new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .append(TIME_SECONDS)
      .optionalStart()
      .appendFraction(NANO_OF_SECOND, 0, 9, true)
      .parseLenient()
      .appendOffset("+HHMM", "Z")
      .parseStrict()
      .toFormatter();
  }

  /**
   * A variation of ISO_OFFSET_DATE_TIME down to milliseconds.
   */
  public static final DateTimeFormatter DATE_TIME;
  static {
    DATE_TIME = new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .append(ISO_LOCAL_DATE)
      .appendLiteral('T')
      .append(TIME)
      .toFormatter();
  }

  /**
   * A variation of ISO_OFFSET_DATE_TIME down to nanoseconds.
   */
  public static final DateTimeFormatter DATE_TIME_NANOSECONDS;
  static {
    DATE_TIME_NANOSECONDS = new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .append(ISO_LOCAL_DATE)
      .appendLiteral('T')
      .append(TIME_NANOSECONDS)
      .toFormatter();
  }

  /**
   * Get standard dateTime formatters.
   *
   * @return A list of standard formatters.
   */
  public static List<DateTimeFormatter> getDateTimeFormatters() {
    DateTimeFormatter startOfDay = new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .append(DateTimeFormatter.ISO_LOCAL_DATE)
      .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
      .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
      .toFormatter();

    return List.of(
      DateTimeFormatter.ISO_OFFSET_DATE_TIME,
      DateTimeFormatter.ISO_ZONED_DATE_TIME,
      DATE_TIME,
      startOfDay
    );
  }

  /**
   * Get standard time formatters.
   *
   * @return A list of standard formatters.
   */
  public static List<DateTimeFormatter> getTimeFormatters() {
    DateTimeFormatter startOfDay = new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .append(DateTimeFormatter.ISO_LOCAL_DATE)
      .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
      .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
      .toFormatter();

    return List.of(
      DateTimeFormatter.ISO_TIME,
      DateTimeFormatter.ISO_OFFSET_DATE_TIME,
      DateTimeFormatter.ISO_ZONED_DATE_TIME,
      TIME,
      DATE_TIME,
      startOfDay
    );
  }

  /**
   * Format the dateTime as a string using format "yyyy-MM-dd".
   * <p>
   * This will normalize the dateTime.
   * <p>
   * This will not alter the time zone.
   *
   * @param dateTime The dateTime to convert to a string.
   * @return The converted dateTime string.
   */
  public static String formatDate(ZonedDateTime dateTime) {
    return defaultToNow(dateTime).format(ISO_LOCAL_DATE);
  }

  /**
   * Format the date as a string using format "yyyy-MM-dd".
   * <p>
   * This will normalize the date.
   * <p>
   * This will not alter the time zone.
   *
   * @param date The date to convert to a string.
   * @return The converted date string.
   */
  public static String formatDate(LocalDate date) {
    return defaultToNow(date).format(ISO_LOCAL_DATE);
  }

  /**
   * Format the dateTime as a string using format "yyyy-MM-dd'T'HH:mm:ss.SSSZZ".
   * <p>
   * This will not normalize the dateTime and will instead return NULL if
   * dateTime is NULL.
   * <p>
   * This will not alter the time zone.
   *
   * @param dateTime The dateTime to convert to a string.
   * @return The converted dateTime string or NULL.
   */
  public static String formatDateTimeOptional(ZonedDateTime dateTime) {
    if (dateTime == null) {
      return null;
    }

    return formatDateTime(dateTime);
  }

  /**
   * Format the Instant as a string using format "yyyy-MM-dd'T'HH:mm:ss.SSSZZ".
   * <p>
   * This will not normalize the dateTime and will instead return NULL if
   * dateTime is NULL.
   * <p>
   * This will not alter the time zone.
   *
   * @param instant The dateTime instant to convert to a string.
   * @return The converted dateTime string or NULL.
   */
  public static String formatDateTimeOptional(Instant instant) {
    if (instant == null) {
      return null;
    }

    return formatDateTime(instant);
  }

  /**
   * Format the dateTime as a string using format "yyyy-MM-dd'T'HH:mm:ss.SSSZZ".
   * <p>
   * This will normalize the dateTime.
   * <p>
   * This will not alter the time zone.
   *
   * @param dateTime The dateTime to convert to a string.
   * @return The converted dateTime string.
   */
  public static String formatDateTime(ZonedDateTime dateTime) {
    return defaultToNow(dateTime).format(DATE_TIME);
  }

  /**
   * Format the Instant as a string using format "yyyy-MM-dd'T'HH:mm:ss.SSSZZ".
   * <p>
   * This will normalize the Instant.
   * <p>
   * This will not alter the time zone.
   *
   * @param dateTime The dateTime to convert to a string.
   * @return The converted dateTime string.
   */
  public static String formatDateTime(Instant instant) {
    return formatDateTime(ZonedDateTime.ofInstant(defaultToNow(instant), ZoneOffset.UTC));
  }

  /**
   * Format the date as a string using format "yyyy-MM-dd'T'HH:mm:ss.SSSZZ".
   * <p>
   * The time is set to Midnight for the ClockUtil's time zone.
   * <p>
   * This will normalize the date.
   * <p>
   * This will not alter the time zone.
   *
   * @param date The date to convert to a string.
   * @return The converted date string.
   */
  public static String formatDateTime(LocalDate date) {
    return ZonedDateTime.of(defaultToNow(date), LocalTime.MIDNIGHT, ClockUtil.getZoneId())
      .format(DATE_TIME);
  }

  /**
   * Parse the string, returning a dateTime using the ClockUtil's time zone.
   * <p>
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   *
   * @param value The value to parse into a ZonedDateTime.
   * @return A dateTime parsed from the value.
   */
  public static ZonedDateTime parseDateTime(String value) {
    return parseDateTime(value, null);
  }

  /**
   * Parse the given value, returning a dateTime.
   * <p>
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   * For compatibility with JodaTime, when zone is null, then use the zone from
   * the ClockUtil.
   *
   * @param value The value to parse into a ZonedDateTime.
   * @param zone The time zone to use when parsing.
   * @return A dateTime parsed from the value.
   */
  public static ZonedDateTime parseDateTime(String value, ZoneId zone) {
    if (value == null) {
      final ZonedDateTime dateTime = defaultToNow((ZonedDateTime) null);

      if (zone == null) {
        return dateTime;
      }

      return dateTime.withZoneSameInstant(zone);
    }

    return parseDateTimeString(value, zone);
  }

  /**
   * Parse the string, returning a dateTime using the ClockUtil's time zone.
   * <p>
   * This will not normalize the dateTime and will instead return NULL if
   * dateTime is NULL.
   *
   * @param value The value to parse into a ZonedDateTime.
   * @return A dateTime parsed from the value.
   */
  public static ZonedDateTime parseDateTimeOptional(String value) {
    return parseDateTimeOptional(value, null);
  }

  /**
   * Parse the given value, returning a dateTime.
   * <p>
   * This will not normalize the dateTime and will instead return NULL if
   * dateTime is NULL.
   * For compatibility with JodaTime, when zone is null, then use the zone from
   * the ClockUtil.
   *
   * @param value The value to parse into a ZonedDateTime.
   * @param zone The time zone to use when parsing.
   * @return A dateTime parsed from the value.
   */
  public static ZonedDateTime parseDateTimeOptional(String value, ZoneId zone) {
    if (value == null) {
      return null;
    }

    return parseDateTimeString(value, zone);
  }

  /**
   * Parse the string, returning an Instant using the ClockUtil's time zone.
   * <p>
   * This will not normalize the Instant and will instead return NULL if
   * dateTime is NULL.
   *
   * @param value The value to parse into an Instant.
   * @return A dateTime parsed from the value.
   */
  public static Instant parseInstantOptional(String value) {
    return parseInstantOptional(value, null);
  }

  /**
   * Parse the given value, returning an Instant.
   * <p>
   * This will not normalize the Instant and will instead return NULL if
   * dateTime is NULL.
   * For compatibility with JodaTime, when zone is null, then use the zone from
   * the ClockUtil.
   *
   * @param value The value to parse into an Instant.
   * @param zone The time zone to use when parsing.
   * @return A dateTime parsed from the value.
   */
  public static Instant parseInstantOptional(String value, ZoneId zone) {
    if (value == null) {
      return null;
    }

    return parseDateTimeString(value, zone).toInstant();
  }

  /**
   * Parse the given value, returning a date using system time zone.
   *
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   *
   * @param value The value to parse into a LocalDate.
   * @return A date parsed from the value.
   */
  public static LocalDate parseDate(String value) {
    return parseDate(value, null);
  }

  /**
   * Parse the given value, returning a date.
   * <p>
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   * For compatibility with JodaTime, when zone is null, then use the zone from
   * the ClockUtil.
   *
   * @param value The value to parse into a LocalDate.
   * @param zone The time zone to use when parsing.
   * @return A date parsed from the value.
   */
  public static LocalDate parseDate(String value, ZoneId zone) {
    if (value == null) {
      return defaultToNow((LocalDate) null);
    }

    List<DateTimeFormatter> formatters = getDateTimeFormatters();

    for (int i = 0; i < formatters.size(); i++) {
      try {
        DateTimeFormatter formatter = formatters.get(i);

        if (zone != null) {
          formatter = formatter.withZone(zone);
        }

        return LocalDate.parse(value, formatter);
      } catch (DateTimeParseException e1) {
        if (i == formatters.size() - 1) {
          throw e1;
        }
      }
    }

    return LocalDate.parse(value);
  }

  /**
   * Parse the given value, returning a date using system time zone.
   *
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   *
   * @param value The value to parse into a LocalTime.
   * @return A date parsed from the value.
   */
  public static LocalTime parseTime(String value) {
    return parseTime(value, null);
  }

  /**
   * Parse the given value, returning a date.
   * <p>
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   * For compatibility with JodaTime, when zone is null, then use the zone from
   * the ClockUtil.
   *
   * @param value The value to parse into a LocalTime.
   * @param zone The time zone to use when parsing.
   * @return A date parsed from the value.
   */
  public static LocalTime parseTime(String value, ZoneId zone) {
    if (value == null) {
      return defaultToNow((LocalTime) null);
    }

    List<DateTimeFormatter> formatters = getTimeFormatters();

    for (int i = 0; i < formatters.size(); i++) {
      try {
        DateTimeFormatter formatter = formatters.get(i);

        if (zone != null) {
          formatter = formatter.withZone(zone);
        }

        return LocalTime.parse(value, formatter);
      } catch (DateTimeParseException e1) {
        if (i == formatters.size() - 1) {
          throw e1;
        }
      }
    }

    return LocalTime.parse(value);
  }

  /**
   * Parse the given value, returning a dateTime.
   *
   * @param value The value to parse into a LocalDate.
   * @param zone The time zone to use when parsing.
   * @return A dateTime parsed from the value.
   */
  private static ZonedDateTime parseDateTimeString(String value, ZoneId zone) {
    List<DateTimeFormatter> formatters = getDateTimeFormatters();

    for (int i = 0; i < formatters.size(); i++) {
      try {
        DateTimeFormatter formatter = formatters.get(i);

        if (zone != null) {
          formatter = formatter.withZone(zone);
        }

        return ZonedDateTime.parse(value, formatter).truncatedTo(ChronoUnit.MILLIS);
      } catch (DateTimeParseException e1) {
        if (i == formatters.size() - 1) {
          throw e1;
        }
      }
    }

    return ZonedDateTime.parse(value).truncatedTo(ChronoUnit.MILLIS);
  }

}
