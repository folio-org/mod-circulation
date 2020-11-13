package org.folio.circulation.support.utils;


import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.stream.Stream;

import org.joda.time.DateTime;

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

  public static ZonedDateTime toJavaDateTime(DateTime dateTime) {
    return Instant.ofEpochMilli(dateTime.getMillis())
      .atZone(ZoneId.of(dateTime.getZone().getID()));
  }

  public static DateTime mostRecentDate(DateTime... dates) {
    return Stream.of(dates)
      .filter(Objects::nonNull)
      .max(DateTime::compareTo)
      .orElse(null);
  }
}
