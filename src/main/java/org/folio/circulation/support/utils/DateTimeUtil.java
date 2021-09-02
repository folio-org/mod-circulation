package org.folio.circulation.support.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.Stream;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * A utility for centralizing common date time operations.
 * <p>
 * Be careful with the differences of withZoneSameInstant() vs
 * withZoneSameLocal().
 * <p>
 * The "SameInstant" version changes the time zone so that the representation
 * changes but the actual date does not.
 * <p>
 * The "SameLocal" version changes the actual time and preserves the time zone.
 */
public class DateTimeUtil {
  private DateTimeUtil() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  /**
   * Given a dateTime, normalize it.
   * <p>
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   *
   * @param dateTime The dateTime to normalize.
   * @return The provided dateTime or if dateTime is null then ClockUtil.getZonedDateTime().
   */
  public static ZonedDateTime normalizeDateTime(ZonedDateTime dateTime) {
    if (dateTime == null) {
      return ClockUtil.getZonedDateTime();
    }

    return dateTime;
  }

  /**
   * Given an offset dateTime, normalize it.
   * <p>
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   *
   * @param dateTime The dateTime to normalize.
   * @return The provided dateTime or if dateTime is null then ClockUtil.getZonedDateTime().
   */
  public static OffsetDateTime normalizeDateTime(OffsetDateTime dateTime) {
    if (dateTime == null) {
      return ClockUtil.getOffsetDateTime();
    }

    return dateTime;
  }

  /**
   * Given a local dateTime, normalize it.
   * <p>
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   *
   * @param dateTime The dateTime to normalize.
   * @return The provided dateTime or if dateTime is null then ClockUtil.getZonedDateTime().
   */
  public static LocalDateTime normalizeDateTime(LocalDateTime dateTime) {
    if (dateTime == null) {
      return ClockUtil.getLocalDateTime();
    }

    return dateTime;
  }

  /**
   * Given a date, normalize it.
   * <p>
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   *
   * @param date The date to normalize.
   * @return The provided date or if date is null then ClockUtil.getZonedDate().
   */
  public static LocalDate normalizeDate(LocalDate date) {
    if (date == null) {
      return ClockUtil.getLocalDate();
    }

    return date;
  }

  /**
   * Given a dateTime, normalize it.
   *
   * The normalization is for making JavaTime backward compatible with
   * JodaTime behavior. Therefore, this does nothing.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to normalize.
   * @return The provided dateTime or if dateTime is null then ClockUtil.getZonedDateTime().
   */
  public static DateTime normalizeDateTime(DateTime dateTime) {
    if (dateTime == null) {
      return ClockUtil.getDateTime();
    }

    return dateTime;
  }

  /**
   * A stub-like function for normalizing the LocalDate.
   *
   * The normalization is for making JavaTime backward compatible with
   * JodaTime behavior. Therefore, this does nothing.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   */
  public static org.joda.time.LocalDate normalizeDate(org.joda.time.LocalDate date) {
    if (date == null) {
      return ClockUtil.getJodaLocalDate();
    }

    return date;
  }

  /**
   * Given a time zone id, normalize it.
   * <p>
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   *
   * @param zone The time zone to normalize.
   * @return The provided time zone or if zone is null a default time zone.
   */
  public static ZoneId normalizeZone(ZoneId zone) {
    if (zone == null) {
      return ClockUtil.getZoneId();
    }

    return zone;
  }

  /**
   * Given a time zone offset, normalize it.
   * <p>
   * For compatibility with JodaTime, when value is null, then a now() call
   * via ClockUtil is used.
   *
   * @param zone The time zone to normalize.
   * @return The provided time zone or if zone is null a default time zone.
   */
  public static ZoneOffset normalizeZone(ZoneOffset zone) {
    if (zone == null) {
      return ClockUtil.getZoneOffset();
    }

    return zone;
  }

  /**
   * A stub-like function for normalizing the DateTimeZone.
   *
   * The normalization is for making JavaTime backward compatible with
   * JodaTime behavior. Therefore, this does nothing.
   *
   * TODO: This should be replaced once migrated from JodaTime to JavaTime.
   */
  public static DateTimeZone normalizeZone(DateTimeZone zone) {
    if (zone == null) {
      return ClockUtil.jodaTimezone();
    }

    return zone;
  }

  /**
   * Get the first second of the year.
   * <p>
   * This operates in the time zone specified by dateTime.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atStartOfYear(ZonedDateTime dateTime) {
    return atStartOfYear(dateTime, dateTime.getZone());
  }

  /**
   * Get the first second of the year for the given time zone.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atStartOfYear(ZonedDateTime dateTime, ZoneId zone) {
    return atStartOfDay(dateTime
      .withZoneSameInstant(zone)
      .withDayOfYear(1));
  }

  /**
   * Get the last second of the year.
   * <p>
   * This operates in the time zone specified by dateTime.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atEndOfYear(ZonedDateTime dateTime) {
    return atEndOfYear(dateTime, dateTime.getZone());
  }

  /**
   * Get the last second of the year for the given time zone.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atEndOfYear(ZonedDateTime dateTime, ZoneId zone) {
    return atEndOfDay(dateTime
      .withZoneSameInstant(zone)
      .withDayOfYear(1)
      .plusYears(1)
      .minusDays(1));
  }

  /**
   * Get the first second of the year for the given time zone.
   * <p>
   * This will truncate to seconds.
   *
   * @param LocalDate The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static ZonedDateTime atStartOfYear(LocalDate localDate, ZoneId zone) {
    return atStartOfDay(localDate.withDayOfYear(1), zone);
  }

  /**
   * Get the last second of the year for the given time zone.
   * <p>
   * This operates in the time zone specified by dateTime.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static ZonedDateTime atEndOfYear(LocalDate localDate, ZoneId zone) {
    return atEndOfDay(localDate
      .withDayOfYear(localDate
        .lengthOfYear()),
      zone);
  }

  /**
   * Get the first second of the year.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static DateTime atStartOfYear(DateTime dateTime) {
    return atStartOfYear(dateTime, dateTime.getZone());
  }

  /**
   * Get the first second of the year.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static DateTime atStartOfYear(DateTime dateTime, DateTimeZone zone) {
    final DateTime zonedDateTime = dateTime.withZone(zone);

    return atStartOfDay(zonedDateTime
      .withDayOfYear(dateTime
        .dayOfYear()
        .getMinimumValue()));
  }

  /**
   * Get the last second of the year.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static DateTime atEndOfYear(DateTime dateTime) {
    return atEndOfYear(dateTime, dateTime.getZone());
  }

  /**
   * Get the last second of the year.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static DateTime atEndOfYear(DateTime dateTime, DateTimeZone zone) {
    final DateTime zonedDateTime = dateTime.withZone(zone);

    return atEndOfDay(zonedDateTime
      .withDayOfYear(zonedDateTime
        .dayOfYear()
        .getMaximumValue()));
  }

  /**
   * Get the first second of the year.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param LocalDate The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static DateTime atStartOfYear(org.joda.time.LocalDate localDate, DateTimeZone zone) {
    return atStartOfDay(localDate, zone)
      .withDayOfYear(localDate
        .dayOfYear()
        .getMinimumValue());
  }

  /**
   * Get the last second of the year.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static DateTime atEndOfYear(org.joda.time.LocalDate localDate, DateTimeZone zone) {
    return atEndOfDay(localDate, zone)
      .withDayOfYear(localDate
        .dayOfYear()
        .getMaximumValue());
  }

  /**
   * Get the first second of the month.
   * <p>
   * This operates in the time zone specified by dateTime.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atStartOfMonth(ZonedDateTime dateTime) {
    return atStartOfMonth(dateTime, dateTime.getZone());
  }

  /**
   * Get the first second of the month for the given time zone.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atStartOfMonth(ZonedDateTime dateTime, ZoneId zone) {
    return atStartOfDay(dateTime
      .withZoneSameInstant(zone)
      .withDayOfMonth(1));
  }

  /**
   * Get the last second of the month.
   * <p>
   * This operates in the time zone specified by dateTime.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atEndOfMonth(ZonedDateTime dateTime) {
    return atEndOfMonth(dateTime, dateTime.getZone());
  }

  /**
   * Get the last second of the month for the given time zone.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atEndOfMonth(ZonedDateTime dateTime, ZoneId zone) {
    return atEndOfDay(dateTime
      .withZoneSameInstant(zone)
      .withDayOfMonth(1)
      .plusMonths(1)
      .minusDays(1));
  }

  /**
   * Get the first second of the month for the given time zone.
   * <p>
   * This will truncate to seconds.
   *
   * @param LocalDate The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static ZonedDateTime atStartOfMonth(LocalDate localDate, ZoneId zone) {
    return atStartOfDay(localDate.withDayOfMonth(1), zone);
  }

  /**
   * Get the last second of the month for the given time zone.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static ZonedDateTime atEndOfMonth(LocalDate localDate, ZoneId zone) {
    return atEndOfDay(localDate
      .withDayOfMonth(localDate
        .lengthOfMonth()),
      zone);
  }

  /**
   * Get the first second of the month.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static DateTime atStartOfMonth(DateTime dateTime) {
    return atStartOfMonth(dateTime, dateTime.getZone());
  }

  /**
   * Get the first second of the month.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static DateTime atStartOfMonth(DateTime dateTime, DateTimeZone zone) {
    final DateTime zonedDateTme = dateTime.withZone(zone);

    return atStartOfDay(zonedDateTme
      .withDayOfMonth(zonedDateTme
        .dayOfMonth()
        .getMinimumValue()));
  }

  /**
   * Get the last second of the month.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static DateTime atEndOfMonth(DateTime dateTime) {
    return atEndOfMonth(dateTime, dateTime.getZone());
  }

  /**
   * Get the last second of the month.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static DateTime atEndOfMonth(DateTime dateTime, DateTimeZone zone) {
    final DateTime zonedDateTme = dateTime.withZone(zone);

    return atEndOfDay(zonedDateTme
      .withDayOfMonth(zonedDateTme
        .dayOfMonth()
        .getMaximumValue()));
  }

  /**
   * Get the first second of the month.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param LocalDate The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static DateTime atStartOfMonth(org.joda.time.LocalDate localDate, DateTimeZone zone) {
    return atStartOfDay(localDate, zone)
      .withDayOfMonth(localDate
        .dayOfMonth()
        .getMinimumValue());
  }

  /**
   * Get the last second of the month.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static DateTime atEndOfMonth(org.joda.time.LocalDate localDate, DateTimeZone zone) {
    return atEndOfDay(localDate, zone)
      .withDayOfMonth(localDate
        .dayOfMonth()
        .getMaximumValue());
  }

  /**
   * Get the first second of the day.
   * <p>
   * This operates in the time zone specified by dateTime.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atStartOfDay(ZonedDateTime dateTime) {
    return atStartOfDay(dateTime, dateTime.getZone());
  }

  /**
   * Get the first second of the day for the given time zone.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atStartOfDay(ZonedDateTime dateTime, ZoneId zone) {
    return dateTime
      .withZoneSameInstant(zone)
      .withHour(0)
      .withMinute(0)
      .withSecond(0)
      .truncatedTo(ChronoUnit.SECONDS);
  }

  /**
   * Get the last second of the day.
   * <p>
   * This operates in the time zone specified by dateTime.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atEndOfDay(ZonedDateTime dateTime) {
    return atEndOfDay(dateTime, dateTime.getZone());
  }

  /**
   * Get the last second of the day for the given time zone.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static ZonedDateTime atEndOfDay(ZonedDateTime dateTime, ZoneId zone) {
    return atStartOfDay(dateTime, zone)
      .plusDays(1)
      .minusSeconds(1)
      .truncatedTo(ChronoUnit.SECONDS);
  }

  /**
   * Get the first second of the day for the given time zone.
   * <p>
   * This will truncate to seconds.
   *
   * @param LocalDate The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static ZonedDateTime atStartOfDay(LocalDate localDate, ZoneId zone) {
    return localDate
      .atStartOfDay(zone)
      .truncatedTo(ChronoUnit.SECONDS);
  }

  /**
   * Get the last second of the day for the given time zone.
   * <p>
   * This will truncate to seconds.
   *
   * @param dateTime The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static ZonedDateTime atEndOfDay(LocalDate localDate, ZoneId zone) {
    return atStartOfDay(localDate, zone)
      .plusDays(1)
      .minusSeconds(1)
      .truncatedTo(ChronoUnit.SECONDS);
  }

  /**
   * Get the first second of the day.
   * <p>
   * This operates in the time zone specified by dateTime.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static DateTime atStartOfDay(DateTime dateTime) {
    return atStartOfDay(dateTime, dateTime.getZone());
  }

  /**
   * Get the first second of the day for the given time zone.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static DateTime atStartOfDay(DateTime dateTime, DateTimeZone zone) {
    return dateTime
      .withZone(zone)
      .withTimeAtStartOfDay()
      .withMillisOfSecond(0);
  }

  /**
   * Get the last second of the day.
   * <p>
   * This operates in the time zone specified by dateTime.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static DateTime atEndOfDay(DateTime dateTime) {
    return atEndOfDay(dateTime, dateTime.getZone());
  }

  /**
   * Get the last second of the day for the given time zone.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @param zone The time zone to use.
   * @return The converted dateTime.
   */
  public static DateTime atEndOfDay(DateTime dateTime, DateTimeZone zone) {
    return atStartOfDay(dateTime, zone)
      .plusDays(1)
      .minusSeconds(1)
      .withMillisOfSecond(0);
  }

  /**
   * Get the first second of the day for the given time zone.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param LocalDate The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static DateTime atStartOfDay(org.joda.time.LocalDate localDate, DateTimeZone zone) {
    return localDate
      .toDateTimeAtStartOfDay(zone)
      .withMillisOfSecond(0);
  }

  /**
   * Get the last second of the day for the given time zone.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The localDate to convert.
   * @param zone The time zone to use.
   * @return The converted localDate.
   */
  public static DateTime atEndOfDay(org.joda.time.LocalDate localDate, DateTimeZone zone) {
    return atStartOfDay(localDate, zone)
      .plusDays(1)
      .minusSeconds(1)
      .withMillisOfSecond(0);
  }

  /**
   * Finds the most recent dateTime from a series of dateTimes.
   *
   * @param dates A series of dateTimes.
   * @return The dateTime that is most recent or NULL if no valid dateTimes
   * provided.
   */
  public static ZonedDateTime mostRecentDate(ZonedDateTime... dates) {
    return Stream.of(dates)
      .filter(Objects::nonNull)
      .max(ZonedDateTime::compareTo)
      .orElse(null);
  }

  /**
   * Finds the most recent dateTime from a series of dateTimes.
   *
   * @param dates A series of dateTimes.
   * @return The dateTime that is most recent or NULL if no valid dateTimes
   * provided.
   */
  public static DateTime mostRecentDate(DateTime... dates) {
    return Stream.of(dates)
      .filter(Objects::nonNull)
      .max(DateTime::compareTo)
      .orElse(null);
  }

  /**
   * Convert from DateTime to ZonedDateTime.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The JodaTime DateTime to convert from.
   * @return The converted dateTime.
   */
  public static ZonedDateTime toZonedDateTime(DateTime dateTime) {
    return java.time.Instant.ofEpochMilli(dateTime.getMillis())
      .atZone(ZoneId.of(dateTime.getZone().getID()));
  }

  /**
   * Convert the JodaTime DateTime to JavaTime OffsetDateTime.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The JodaTime DateTime to convert from.
   * @return The converted dateTime.
   */
  public static OffsetDateTime toOffsetDateTime(DateTime dateTime) {
    final var instant = java.time.Instant.ofEpochMilli(dateTime.getMillis());

    return OffsetDateTime.ofInstant(instant,
      ZoneId.of(dateTime.getZone().getID()));
  }

}
