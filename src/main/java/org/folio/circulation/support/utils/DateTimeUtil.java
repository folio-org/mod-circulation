package org.folio.circulation.support.utils;


import java.time.ZonedDateTime;

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
}
