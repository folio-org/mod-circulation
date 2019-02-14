package org.folio.circulation.domain.policy.library;

import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.List;

import static org.folio.circulation.domain.policy.LoanPolicyPeriod.isShortTermLoans;

public final class ClosedLibraryStrategyUtils {

  public static final LocalTime END_OF_A_DAY = LocalTime.MIDNIGHT.minusSeconds(1);

  private ClosedLibraryStrategyUtils() {
  }

  public static ClosedLibraryStrategy determineClosedLibraryStrategy(
    LoanPolicy loanPolicy, DateTime startDate, DateTimeZone zone) {
    DueDateManagement dueDateManagement = loanPolicy.getDueDateManagement();
    LoanPolicyPeriod loanPeriod = loanPolicy.getPeriodInterval();
    int periodDuration = loanPolicy.getPeriodDuration();
    LoanPolicyPeriod offsetInterval = loanPolicy.getOffsetPeriodInterval();
    int offsetDuration = loanPolicy.getOffsetPeriodDuration();

    switch (dueDateManagement) {
      case MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY:
        return new EndOfPreviousDayStrategy(zone);
      case MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY:
        return new EndOfNextOpenDayStrategy(zone);
      case MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS:
        return new EndOfCurrentHoursStrategy(loanPeriod, periodDuration, startDate, zone);
      case MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS:
        return new BeginningOfNextOpenHoursStrategy(loanPeriod, periodDuration,
          offsetInterval, offsetDuration, startDate, zone);
      case KEEP_THE_CURRENT_DUE_DATE:
        return new KeepCurrentDateStrategy(zone);
      case KEEP_THE_CURRENT_DUE_DATE_TIME:
      default:
        return new KeepCurrentDateTimeStrategy();
    }
  }

  public static ClosedLibraryStrategy determineStrategyForMovingBackward(
    LoanPolicy loanPolicy, DateTime startDate, DateTimeZone zone) {
    LoanPolicyPeriod loanPeriod = loanPolicy.getPeriodInterval();
    int periodDuration = loanPolicy.getPeriodDuration();

    return isShortTermLoans(loanPeriod)
      ? new EndOfCurrentHoursStrategy(loanPeriod, periodDuration, startDate, zone)
      : new EndOfPreviousDayStrategy(zone);
  }

  public static DateTime getLatestClosingHours(OpeningDay openingDay, DateTimeZone zone) {
    boolean allDay = openingDay.getAllDay();
    LocalDate localDate = openingDay.getDate();
    if (allDay) {
      return localDate.toDateTime(END_OF_A_DAY, zone);
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();
      if (openingHours.isEmpty()) {
        return localDate.toDateTime(END_OF_A_DAY, zone);
      } else {
        OpeningHour openingHour = openingHours.get(openingHours.size() - 1);
        LocalTime endTime = openingHour.getEndTime();
        return localDate.toDateTime(endTime, zone);
      }
    }
  }

  /**
   * Find the time of the end of the period by taking into account the time shift
   */
  public static LocalTime findEndTimeOfOpeningPeriod(List<OpeningHour> openingHoursList, LocalTime time) {
    LocalTime endTimePeriod = openingHoursList.get(openingHoursList.size() - 1).getEndTime();
    if (time.isAfter(endTimePeriod)) {
      return endTimePeriod;
    }

    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = openingHoursList.get(i).getStartTime();
      LocalTime endTimeFirst = openingHoursList.get(i).getEndTime();
      LocalTime startTimeSecond = openingHoursList.get(i + 1).getStartTime();
      if (time.isAfter(startTimeFirst) && time.isBefore(startTimeSecond)) {
        return endTimeFirst;
      }
    }
    return openingHoursList.get(0).getEndTime();
  }
}
