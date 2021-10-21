package org.folio.circulation.support.utils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A clock manager for safely getting and setting the time.
 * <p>
 * Provides management of the clock that is then exposed and used as the
 * default clock for all methods within this utility.
 * <p>
 * Use these methods rather than using the now() methods for any given date and
 * time related class.
 * Failure to do so may result in the inability to properly perform tests.
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
   *   A LocalTime as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static LocalTime getLocalTime() {
    return LocalTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
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

}
