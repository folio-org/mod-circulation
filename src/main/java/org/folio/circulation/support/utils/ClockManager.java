package org.folio.circulation.support.utils;

import static java.time.ZoneOffset.UTC;

import java.time.Clock;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;

/**
 * A clock manager for safely getting and setting the time.
 */
public class ClockManager {
  private static Clock clock = Clock.systemUTC();

  private ClockManager() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  /**
   * Set the clock assigned to the clock manager to a given clock.
   */
  public static void setClock(Clock clock) {
    if (clock == null) {
      throw new IllegalArgumentException("clock cannot be null");
    }

    ClockManager.clock = clock;
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
   * TODO: This is temporarily designed to work with JodaTime. Replace this as
   * appropriate when migrating from JodaTime to JavaTime.
   *
   * @return
   *   A DateTime as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static DateTime getDateTime() {
    return jodaInstant().toDateTime(DateTimeZone.UTC);
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * TODO: This is temporarily designed to work with JodaTime. Replace this as
   * appropriate when migrating from JodaTime to JavaTime.
   *
   * @return
   *   A LocalDateTime as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static LocalDateTime getLocalDateTime() {
    final DateTime now = jodaInstant().toDateTime(getDateTimeZone());
    return now.toLocalDateTime();
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * TODO: This is temporarily designed to work with JodaTime. Replace this as
   * appropriate when migrating from JodaTime to JavaTime.
   *
   * @return
   *   A LocalDate as if now() is called.
   */
  public static LocalDate getLocalDate() {
    final DateTime now = jodaInstant().toDateTime(getDateTimeZone());
    return now.toLocalDate();
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * TODO: This is temporarily designed to work with JodaTime. Replace this as
   * appropriate when migrating from JodaTime to JavaTime.
   *
   * @return
   *   A LocalDate as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static LocalTime getLocalTime() {
    final DateTime now = jodaInstant().toDateTime(getDateTimeZone());
    return now.toLocalTime();
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * TODO: This is temporarily designed to work with JodaTime. Replace this as
   * appropriate when migrating from JodaTime to JavaTime.
   *
   * @return
   *   An Instant as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static org.joda.time.Instant getInstant() {
    return jodaInstant();
  }

  /**
   * Get the timezone of the system clock according to the clock manager.
   *
   * TODO: This is temporarily designed to work with JodaTime. Replace this as
   * appropriate when migrating from JodaTime to JavaTime.
   *
   * @return
   *   The current timezone as a ZoneId.
   */
  public static DateTimeZone getDateTimeZone() {
    return jodaTimezone();
  }

  /**
   * Get the Timezone from the clock.
   *
   * This will be converted from JavaTimes timezone to JodaTimes timezone.
   *
   * TODO: Remove this once JodaTime is fully converted to JavaTime.
   */
  private static DateTimeZone jodaTimezone() {
    String zoneId = clock.getZone().getId();

    // JavaTime uses "Z" for UTC but JodaTime uses "UTC".
    if (zoneId.equals(UTC.getId())) {
      zoneId = "UTC";
    }

    return DateTimeZone.forID(zoneId);
  }

  /**
   * Get the current time from the clock as an Instant.
   *
   * This will be converted from JavaTimes Instant to JodaTimes Instant.
   *
   * TODO: Remove this once JodaTime is fully converted to JavaTime.
   */
  private static org.joda.time.Instant jodaInstant() {
    java.time.Instant now = clock.instant();
    return org.joda.time.Instant.ofEpochMilli(now.toEpochMilli());
  }
}
