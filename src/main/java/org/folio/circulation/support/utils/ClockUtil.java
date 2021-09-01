package org.folio.circulation.support.utils;

import static java.time.ZoneOffset.UTC;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * A clock manager for safely getting and setting the time.
 */
public class ClockUtil {
  private static Clock clock = Clock.systemUTC();

  private ClockUtil() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  /**
   * Set the clock assigned to the clock manager to a given clock.
   */
  public static void setClock(Clock clock) {
    if (clock == null) {
      throw new IllegalArgumentException("clock cannot be null");
    }

    ClockUtil.clock = clock;
  }

  /**
   * Set the clock assigned to the clock manager to the system clock.
   */
  public static void setDefaultClock() {
    clock = Clock.systemUTC();
  }

  /**
   * Get the clock assigned the the clock manager.
   *
   * @return
   *   The clock currently being used by ClockManager.
   */
  public static Clock getClock() {
    return clock;
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * @return
   *   A ZonedDateTime as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static ZonedDateTime getZonedDateTime() {
    return ZonedDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * @return
   *   An OffsetDateTime as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static OffsetDateTime getOffsetDateTime() {
    return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * TODO: This is temporarily designed to work with JodaTime. Replace this as
   * appropriate when migrating from JodaTime to JavaTime.
   *
   * @return
   *   A DateTime as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static DateTime getDateTime() {
    return getJodaInstant().toDateTime(DateTimeZone.UTC);
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * @return
   *   A LocalDateTime as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static LocalDateTime getLocalDateTime() {
    return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * @return
   *   A LocalDate as if now() is called.
   */
  public static LocalDate getLocalDate() {
    return LocalDate.now(clock);
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * @return
   *   A LocalDate as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static LocalTime getLocalTime() {
    return LocalTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * TODO: Remove this once JodaTime is fully converted to JavaTime.
   *
   * @return
   *   A LocalDateTime as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static org.joda.time.LocalDateTime getJodaLocalDateTime() {
    final DateTime now = getJodaInstant().toDateTime(getDateTimeZone());
    return now.toLocalDateTime();
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * TODO: Remove this once JodaTime is fully converted to JavaTime.
   *
   * @return
   *   A LocalDate as if now() is called.
   */
  public static org.joda.time.LocalDate getJodaLocalDate() {
    final DateTime now = getJodaInstant().toDateTime(getDateTimeZone());
    return now.toLocalDate();
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * TODO: Remove this once JodaTime is fully converted to JavaTime.
   *
   * @return
   *   A LocalDate as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static org.joda.time.LocalTime getJodaLocalTime() {
    final DateTime now = getJodaInstant().toDateTime(getDateTimeZone());
    return now.toLocalTime();
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * @return
   *   An Instant as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static Instant getInstant() {
    return clock.instant();
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * TODO: Remove this once JodaTime is fully converted to JavaTime.
   *
   * @return
   *   An Instant as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static org.joda.time.Instant getJodaInstant() {
    java.time.Instant now = clock.instant();
    return org.joda.time.Instant.ofEpochMilli(now.toEpochMilli());
  }

  /**
   * Get the time zone of the system clock according to the clock manager.
   *
   * TODO: Remove this once JodaTime is fully converted to JavaTime.
   *
   * @return
   *   The current time zone as a ZoneId.
   */
  public static DateTimeZone getDateTimeZone() {
    return jodaTimezone();
  }

  /**
   * Get the time zone of the system clock according to the clock manager.
   *
   * @return
   *   The current time zone as a ZoneId.
   */
  public static ZoneId getZoneId() {
    return clock.getZone();
  }

  /**
   * Get the time zone of the system clock according to the clock manager.
   *
   * @return
   *   The current time zone as a ZoneOffset.
   */
  public static ZoneOffset getZoneOffset() {
    return ZoneOffset.of(clock.getZone().getRules().getOffset(clock.instant())
      .getId());
  }

  /**
   * Get the time zone from the clock.
   *
   * This will be converted from JavaTimes time zone to JodaTimes time zone.
   *
   * TODO: Remove this once JodaTime is fully converted to JavaTime.
   */
  public static DateTimeZone jodaTimezone() {
    String zoneId = clock.getZone().getId();

    // JavaTime uses "Z" for UTC but JodaTime uses "UTC".
    if (zoneId.equals(UTC.getId())) {
      zoneId = "UTC";
    }

    return DateTimeZone.forID(zoneId);
  }
}
