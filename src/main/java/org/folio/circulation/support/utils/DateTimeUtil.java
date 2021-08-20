package org.folio.circulation.support.utils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
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
   * Convert from DateTime to ZonedDateTime.
   *
   * TODO: This should be removed once migrated from JodaTime to JavaTime.
   *
   * @param dateTime
   * @return
   */
  public static ZonedDateTime toZonedDateTime(DateTime dateTime) {
    return java.time.Instant.ofEpochMilli(dateTime.getMillis())
      .atZone(ZoneId.of(dateTime.getZone().getID()));
  }

  /**
   * A stub-like function for normalizing the DateTimeZone.
   *
   * The normalization is for making JavaTime backward compatible with
   * JodaTime behavior. Therefore, this does nothing.
   *
   * TODO: This should be replaced once migrated from JodaTime to JavaTime.
   */
  public static DateTimeZone normalizeDateTimeZone(DateTimeZone zone) {
    return zone;
  }

  /**
   * A stub-like function for normalizing the DateTime.
   *
   * The normalization is for making JavaTime backward compatible with
   * JodaTime behavior. Therefore, this does nothing.
   *
   * TODO: This should be replaced once migrated from JodaTime to JavaTime.
   */
  public static DateTime normalizeDateTimeZone(DateTime dateTime) {
    return dateTime;
  }

  /**
   * A stub-like function for normalizing the LocalDateTime.
   *
   * The normalization is for making JavaTime backward compatible with
   * JodaTime behavior. Therefore, this does nothing.
   *
   * TODO: This should be replaced once migrated from JodaTime to JavaTime.
   */
  public static org.joda.time.LocalDateTime normalizeDateTimeZone(org.joda.time.LocalDateTime dateTime) {
    return dateTime;
  }

  /**
   * A stub-like function for normalizing the LocalDate.
   *
   * The normalization is for making JavaTime backward compatible with
   * JodaTime behavior. Therefore, this does nothing.
   *
   * TODO: This should be replaced once migrated from JodaTime to JavaTime.
   */
  public static org.joda.time.LocalDate normalizeDate(org.joda.time.LocalDate date) {
    return date;
  }

  /**
   * A stub-like function for normalizing the LocalTime.
   *
   * The normalization is for making JavaTime backward compatible with
   * JodaTime behavior. Therefore, this does nothing.
   *
   * TODO: This should be replaced once migrated from JodaTime to JavaTime.
   */
  public static org.joda.time.LocalTime normalizeDate(org.joda.time.LocalTime time) {
    return time;
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
  public static ZonedDateTime atStartOfTheDay(ZonedDateTime dateTime) {
    return dateTime.withHour(0).withMinute(0).withSecond(0)
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
  public static ZonedDateTime atEndOfTheDay(ZonedDateTime dateTime) {
    return dateTime.withHour(23).withMinute(59).withSecond(59)
      .truncatedTo(ChronoUnit.SECONDS);
  }

  /**
   * Get the last second of the day.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static DateTime atStartOfTheDay(DateTime dateTime) {
    return dateTime.withTimeAtStartOfDay();
  }

  /**
   * Get the last second of the day.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The dateTime to convert.
   * @return The converted dateTime.
   */
  public static DateTime atEndOfTheDay(DateTime dateTime) {
    return dateTime.withTime(23, 59, 59, 0);
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
   * Convert the JodaTime DateTime to JavaTime OffsetDateTime.
   *
   * TODO: Remove this after migrating from JodaTime to JavaTime.
   *
   * @param dateTime The JodaTime DateTime to convert from.
   * @return The converted dateTime.
   */
  public static OffsetDateTime toOffsetDateTime(DateTime dateTime) {
    final var instant = java.time.Instant.ofEpochMilli(dateTime.getMillis());

    return OffsetDateTime.ofInstant(instant, ZoneId.of(dateTime.getZone().getID()));
  }

  /**
   * Convert the Local Date Time to a dateTime.
   *
   * TODO: This is temporarily designed to work with JodaTime. Replace this as
   * appropriate when migrating from JodaTime to JavaTime.
   *
   * @param date The date to convert.
   * @param time The time to convert.
   * @return The converted dateTime.
   */
  public static DateTime toDateTime(org.joda.time.LocalDate date, org.joda.time.LocalTime time) {
    return date.toDateTime(time, ClockManager.getDateTimeZone());
  }

  /**
   * Convert the Local Date Time to a dateTime set to UTC.
   *
   * TODO: This is temporarily designed to work with JodaTime. Replace this as
   * appropriate when migrating from JodaTime to JavaTime.
   *
   * @param date The date to convert.
   * @param time The time to convert.
   * @return The converted dateTime.
   */
  public static DateTime toUtcDateTime(org.joda.time.LocalDate date, org.joda.time.LocalTime time) {
    return date.toDateTime(time, DateTimeZone.UTC);
  }

  /**
   * Get the start of the day in the time zone of the current Clock.
   *
   * TODO: This is temporarily designed to work with JodaTime. Replace this as
   * appropriate when migrating from JodaTime to JavaTime.
   *
   * @param localDate The local date to convert from.
   * @return The converted dateTime.
   */
  public static DateTime toStartOfDayDateTime(org.joda.time.LocalDate date) {
    return date.toDateTimeAtStartOfDay();
  }

  /**
   * Get the start of the day in the UTC.
   *
   * TODO: This is temporarily designed to work with JodaTime. Replace this as
   * appropriate when migrating from JodaTime to JavaTime.
   *
   * @param localDate The local date to convert from.
   * @return The converted dateTime.
   */
  public static DateTime toUtcStartOfDayDateTime(org.joda.time.LocalDate date) {
    return date.toDateTimeAtStartOfDay(DateTimeZone.UTC);
  }

}
