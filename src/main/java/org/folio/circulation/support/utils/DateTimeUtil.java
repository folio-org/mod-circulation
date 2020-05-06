package org.folio.circulation.support.utils;


import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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

  public static ZonedDateTime toJavaDateTime(DateTime dateTime) {
    return Instant.ofEpochMilli(dateTime.getMillis())
      .atZone(ZoneId.of(dateTime.getZone().getID()));
  }
}
