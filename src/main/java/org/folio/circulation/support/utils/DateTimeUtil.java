package org.folio.circulation.support.utils;


import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.LocalTime.MIDNIGHT;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.stream.Stream;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

public class DateTimeUtil {
  private DateTimeUtil() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static ZonedDateTime atEndOfTheDay(ZonedDateTime dateTime) {
    return dateTime
      .withHour(23)
      .withMinute(59)
      .withSecond(59);
  }

  public static DateTime atEndOfTheDay(DateTime dateTime) {
    return dateTime.withTime(23, 59, 59, 0);
  }

  public static OffsetDateTime toOffsetDateTime(DateTime dateTime) {
    final var instant = Instant.ofEpochMilli(dateTime.getMillis());

    return OffsetDateTime.ofInstant(instant, ZoneId.of(dateTime.getZone().getID()));
  }

  public static ZonedDateTime toZonedDateTime(DateTime dateTime) {
    return Instant.ofEpochMilli(dateTime.getMillis())
      .atZone(ZoneId.of(dateTime.getZone().getID()));
  }

  public static DateTime mostRecentDate(DateTime... dates) {
    return Stream.of(dates)
      .filter(Objects::nonNull)
      .max(DateTime::compareTo)
      .orElse(null);
  }

  public static DateTime toUtcDateTime(LocalDate date, LocalTime time) {
    return toZonedDateTime(date, time, UTC);
  }

  private static DateTime toZonedDateTime(LocalDate date, LocalTime time, DateTimeZone zone) {
    return date.toDateTime(time).withZoneRetainFields(zone);
  }

  public static DateTime toStartOfDayDateTime(LocalDate localDate) {
    return toUtcDateTime(localDate, MIDNIGHT);
  }
}
