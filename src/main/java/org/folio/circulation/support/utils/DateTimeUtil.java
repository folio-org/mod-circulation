package org.folio.circulation.support.utils;


import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

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

  public static boolean isLongTermPeriod(ChronoUnit chronoUnit) {
    return chronoUnit == ChronoUnit.DAYS
      || chronoUnit == ChronoUnit.WEEKS || chronoUnit == ChronoUnit.MONTHS;
  }
}
