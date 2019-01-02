package org.folio.circulation.support;

import org.folio.circulation.domain.OpeningDayPeriod;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.joda.time.DateTime;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PeriodUtil {

  private PeriodUtil() {
    // not use
  }

  /**
   * Determine whether the offset time is in the time period of the currentDayPeriod
   *
   * @param currentDayPeriod OpeningDayPeriod with the hourly period
   * @param offsetTime       LocalTime with some offset hour or minutes
   * @return true if OpeningDayPeriod is contains offsetTime in the hourly period
   */
  public static boolean isOffsetTimeInCurrentDayPeriod(OpeningDayPeriod currentDayPeriod, LocalTime offsetTime) {

    if (!currentDayPeriod.getOpeningDay().getOpen()) {
      return false;
    }

    boolean isInOpenPeriodsOfDAy = currentDayPeriod.getOpeningDay().getOpeningHour().stream()
      .anyMatch(period -> {
        LocalTime startTime = LocalTime.parse(period.getStartTime());
        LocalTime endTime = LocalTime.parse(period.getEndTime());
        return isInPeriod(offsetTime, startTime, endTime);
      });

    return isInPeriodOfDay(currentDayPeriod, offsetTime, isInOpenPeriodsOfDAy);
  }

  public static boolean isTimeInHourPeriod(OpeningHour period, LocalTime time) {
    LocalTime startTime = LocalTime.parse(period.getStartTime());
    LocalTime endTime = LocalTime.parse(period.getEndTime());
    return startTime.equals(time) || (time.isAfter(startTime) && time.isBefore(endTime));
  }


  private static boolean isInPeriodOfDay(OpeningDayPeriod currentDayPeriod, LocalTime offsetTime, boolean isInOpenPeriodsOfDAy) {

    if (isInOpenPeriodsOfDAy && currentDayPeriod.getOpeningDay().getOpeningHour().size() == 1) {
      return false;
    }

    List<String> collect = currentDayPeriod.getOpeningDay().getOpeningHour().stream()
      .flatMap(period -> Stream.of(period.getStartTime(), period.getEndTime()))
      .collect(Collectors.toList());

    LocalTime startTime = LocalTime.parse(collect.get(0));
    LocalTime endTime = LocalTime.parse(collect.get(collect.size() - 1));

    return isInPeriod(offsetTime, startTime, endTime);
  }

  private static boolean isInPeriod(LocalTime offsetTime, LocalTime startTime, LocalTime endTime) {
    return (offsetTime.isAfter(startTime) && offsetTime.isBefore(endTime))
      || (offsetTime.equals(startTime) || offsetTime.equals(endTime));
  }

  /**
   * Determine whether the offset date is in the time period of the incoming current date
   *
   * @param currentLocalDateTime incoming LocalDateTime
   * @param offsetLocalDateTime  LocalDateTime with some offset days / hour / minutes
   * @return true if offsetLocalDateTime is contains offsetLocalDateTime in the time period
   */
  public static boolean isInCurrentLocalDateTime(LocalDateTime currentLocalDateTime, LocalDateTime offsetLocalDateTime) {
    return offsetLocalDateTime.isBefore(currentLocalDateTime) || offsetLocalDateTime.isEqual(currentLocalDateTime);
  }

  public static LocalTime calculateOffsetTime(LocalTime offsetTime, LoanPolicyPeriod offsetInterval, int offsetDuration) {
    switch (offsetInterval) {
      case HOURS:
        return offsetTime.plusHours(offsetDuration);
      case MINUTES:
        return offsetTime.plusMinutes(offsetDuration);
      default:
        return offsetTime;
    }
  }

  public static DateTime calculateOffset(LocalDateTime localDateTime, LoanPolicyPeriod offsetInterval, int offsetDuration) {
    switch (offsetInterval) {
      case HOURS:
        return new DateTime(localDateTime.plusHours(offsetDuration).toString());
      case MINUTES:
        return new DateTime(localDateTime.plusMinutes(offsetDuration).toString());
      default:
        return new DateTime(localDateTime.toString());
    }
  }
}
