package org.folio.circulation.support;

import org.folio.circulation.domain.OpeningHour;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.List;

public class PeriodUtil {

  private PeriodUtil() {
    // not use
  }

  /**
   * Determine whether time is in any of the periods
   */
  public static boolean isInPeriodOpeningDay(List<OpeningHour> openingHoursList, LocalTime timeShift) {
    return openingHoursList.stream()
      .anyMatch(hours -> isTimeInCertainPeriod(timeShift,
        hours.getStartTime(), hours.getEndTime()));
  }

  /**
   * Determine whether the `time` is within a period `startTime` and `endTime`
   */
  private static boolean isTimeInCertainPeriod(LocalTime time, LocalTime startTime, LocalTime endTime) {
    return !time.isBefore(startTime) && !time.isAfter(endTime);
  }

  public static boolean isAfterDate(DateTime first, DateTime second) {
    LocalDate firstDate = first.toLocalDate();
    LocalDate secondDate = second.toLocalDate();
    return firstDate.isAfter(secondDate);
  }
}
