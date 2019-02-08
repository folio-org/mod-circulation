package org.folio.circulation.support;

import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import org.joda.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        LocalTime.parse(hours.getStartTime()), LocalTime.parse(hours.getEndTime())));
  }

  /**
   * Determine whether the `time` is within a period `startTime` and `endTime`
   */
  private static boolean isTimeInCertainPeriod(LocalTime time, LocalTime startTime, LocalTime endTime) {
    return (time.isAfter(startTime) && time.isBefore(endTime))
      || (time.equals(startTime) || time.equals(endTime));
  }

  /**
   * Determine whether the time shift is in the current day or period
   */
  public static boolean isDateTimeWithDurationInsideDay(OpeningDay openingDay, LocalTime timeShift) {
    if (openingDay.getAllDay()) {
      return true;
    }

    LocalTime[] startEndTime = getStartAndEndTime(openingDay.getOpeningHour());
    LocalTime startTime = startEndTime[0];
    LocalTime endTime = startEndTime[1];
    return isTimeInCertainPeriod(timeShift, startTime, endTime);
  }

  public static LocalTime getTimeShift(LocalTime time, LoanPolicyPeriod period, int duration) {
    return (LoanPolicyPeriod.MINUTES == period) ?
      time.plusMinutes(duration) : time.plusHours(duration);
  }

  /**
   * Get start and end time in the  period
   * The method allows to determine the boundary values ​​of the day period.
   */
  public static LocalTime[] getStartAndEndTime(List<OpeningHour> openingHours) {
    List<String> collect = openingHours.stream()
      .flatMap(hours -> Stream.of(hours.getStartTime(), hours.getEndTime()))
      .collect(Collectors.toList());

    LocalTime startTime = LocalTime.parse(collect.get(0));
    LocalTime endTime = LocalTime.parse(collect.get(collect.size() - 1));
    return new LocalTime[]{startTime, endTime};
  }

  public static boolean isAfterDate(DateTime first, DateTime second) {
    LocalDate firstDate = first.toLocalDate();
    LocalDate secondDate = second.toLocalDate();
    return firstDate.isAfter(secondDate);
  }
}
