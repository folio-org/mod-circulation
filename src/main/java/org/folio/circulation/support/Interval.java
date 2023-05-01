package org.folio.circulation.support;

import static java.time.Duration.ofMillis;
import static java.time.Instant.ofEpochMilli;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.ClockUtil.getInstant;
import static org.folio.circulation.support.utils.ClockUtil.getLocalDateTime;
import static org.folio.circulation.support.utils.ClockUtil.getZoneId;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isSameMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.millisBetween;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.folio.circulation.support.utils.DateTimeUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.With;

/**
 * A simple time interval using an inclusive begin and a duration.
 * <p>
 * The begin represents a zoned date and time in which the interval
 * begins, inclusively.
 * <p>
 * The duration represents how long from the begin date and time.
 * <p>
 * This is immutable.
 */
@AllArgsConstructor
@Builder
@Value
@With
@ToString
public class Interval {

  private final LocalDateTime begin;
  private final ZoneId zone;
  private final Duration duration;

  /**
   * Initialize to current time at UTC and a 0 duration.
   */
  public Interval() {
    begin = getLocalDateTime();
    zone = getZoneId();
    this.duration = ofMillis(0L);
  }

  /**
   * Initialize from date and time range, with time zone.
   * <p>
   * If "begin" is greater than "end", then duration is set to 0.
   *
   * @param begin The inclusive begin time.
   * @param end The exclusive end time.
   */
  public Interval(ZonedDateTime begin, ZonedDateTime end) {
    zone = begin.getZone();
    this.begin = begin.withZoneSameInstant(UTC).toLocalDateTime();
    this.duration = ofMillis(millisBetween(begin, end));
  }

  /**
   * Initialize from date and time range, without time zone.
   * <p>
   * If "begin" is greater than "end", then duration is set to 0.
   * <p>
   * This will use the UTC to locally represent the range.
   *
   * @param begin The inclusive begin time.
   * @param end The exclusive end time.
   */
  public Interval(LocalDateTime begin, LocalDateTime end) {
    zone = UTC;
    this.begin = begin;
    this.duration = ofMillis(millisBetween(begin, end));
  }

  /**
   * Initialize from date and time range, in Epoch milliseconds.
   * <p>
   * If "begin" is greater than "end", then duration is set to 0.
   * <p>
   * This will default to the time zone specified in the ClockManager.
   *
   * @param begin The inclusive begin time, in Epoch milliseconds.
   * @param end The exclusive end time, in Epoch milliseconds.
   */
  public Interval(long begin, long end) {
    this(begin, end, getZoneId());
  }

  /**
   * Initialize from date and time range, in Epoch milliseconds.
   * <p>
   * If "begin" is greater than "end", then duration is set to 0.
   *
   * @param begin The inclusive begin time, in Epoch milliseconds.
   * @param end The exclusive end time, in Epoch milliseconds.
   * @param zone The time zone both begin time and end time are representative
   * of.
   */
  public Interval(long begin, long end, ZoneId zone) {
    // Begin must be converted to UTC, so use a ZonedDateTime to convert.
    final ZonedDateTime dateTime = ZonedDateTime.ofInstant(ofEpochMilli(begin), zone);

    this.zone = zone;
    this.begin = dateTime.withZoneSameInstant(UTC).toLocalDateTime();
    this.duration = ofMillis(DateTimeUtil.millisBetween(begin, end));
  }

  /**
   * Determine if passed date and time is within the duration.
   * <p>
   * The begin is inclusive and the end is exclusive.
   *
   * @param dateTime The date to compare against.
   * @return A boolean of if the dateTime was found within the duration.
   */
  public boolean contains(ZonedDateTime dateTime) {
    final LocalDateTime localDateTime = dateTime.withZoneSameInstant(UTC)
      .toLocalDateTime();

    return isSameMillis(begin, localDateTime) || isAfterMillis(localDateTime, begin)
      && isBeforeMillis(localDateTime, begin.plus(duration));
  }

  /**
   * Retrieve the date and time the interval inclusively begins.
   *
   * @return The date and time.
   */
  public ZonedDateTime getStart() {
    return ZonedDateTime.of(begin, UTC).withZoneSameInstant(zone);
  }

  /**
   * Retrieve the date and time the interval inclusively begins.
   *
   * @return The date and time.
   */
  public ZonedDateTime getEnd() {
    return ZonedDateTime.of(begin.plus(duration), UTC).withZoneSameInstant(zone);
  }

  /**
   * Retrieve the time zone information.
   *
   * @return The time zone.
   */
  public ZoneId getZone() {
    return zone;
  }

  /**
   * Determine if the intervals are immediately before or after without
   * overlapping.
   *
   * @param interval The interval to compare, set to null for now().
   * @return true if abuts, false otherwise.
   */
  public boolean abuts(Interval interval) {
    final ZonedDateTime dateTime = ZonedDateTime.of(begin, UTC);
    final long start = dateTime.toInstant().toEpochMilli();
    final long end = getEnd().toInstant().toEpochMilli();

    if (interval == null) {
      final long now = getInstant().toEpochMilli();
      return now == start || now == end;
    }

    return interval.getEnd().toInstant().toEpochMilli() == start ||
      interval.getStart().toInstant().toEpochMilli() == end;
  }

  /**
   * Determine the difference in time between the intervals.
   *
   * @param interval The interval to use.
   * @return A new interval representing the gap or null if no gap found.
   */
  public Interval gap(Interval interval) {
    final ZonedDateTime dateTime = ZonedDateTime.of(begin, UTC);
    final long start = dateTime.toInstant().toEpochMilli();
    final long end = getEnd().toInstant().toEpochMilli();
    final long iStart = interval.getStart().toInstant().toEpochMilli();
    final long iEnd = interval.getEnd().toInstant().toEpochMilli();

    if (start > iEnd) {
      return new Interval(iEnd, start);
    }
    else if (iStart > end) {
      return new Interval(end, iStart);
    }

    return null;
  }
}
