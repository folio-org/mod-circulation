package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.List;
import java.util.function.BiPredicate;

import static org.folio.circulation.domain.policy.LoanPolicyPeriod.isShortTermLoans;

public final class ClosedLibraryStrategyUtils {

  public static final String DATE_TIME_FORMAT = "yyyy-MM-dd'Z'";
  public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern(DATE_TIME_FORMAT);
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
    BiPredicate<DateTime, AdjustingOpeningDays> libraryIsOpenPredicate =
      LibraryIsOpenPredicate.fromLoanPeriod(loanPeriod);

    switch (dueDateManagement) {
      case MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY:
        return new EndOfPreviousDayStrategy(libraryIsOpenPredicate, zone);
      case MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY:
        return new EndOfNextOpenDayStrategy(libraryIsOpenPredicate, zone);
      case MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS:
        return new EndOfCurrentHoursStrategy(loanPeriod, periodDuration, startDate, libraryIsOpenPredicate, zone);
      case MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS:
        return new BeginningOfNextOpenHoursStrategy(loanPeriod, periodDuration,
          offsetInterval, offsetDuration, startDate, libraryIsOpenPredicate, zone);
      case KEEP_THE_CURRENT_DUE_DATE:
      case KEEP_THE_CURRENT_DUE_DATE_TIME:
      default:
        return new KeepCurrentStrategy();
    }
  }

  public static ClosedLibraryStrategy determineStrategyForMovingBackward(
    LoanPolicy loanPolicy, DateTime startDate, DateTimeZone zone) {
    LoanPolicyPeriod loanPeriod = loanPolicy.getPeriodInterval();
    int periodDuration = loanPolicy.getPeriodDuration();
    BiPredicate<DateTime, AdjustingOpeningDays> libraryIsOpenPredicate =
      LibraryIsOpenPredicate.fromLoanPeriod(loanPeriod);


    return isShortTermLoans(loanPeriod)
      ? new EndOfCurrentHoursStrategy(loanPeriod, periodDuration, startDate, libraryIsOpenPredicate, zone)
      : new EndOfPreviousDayStrategy(libraryIsOpenPredicate, zone);
  }

  public static DateTime getTermDueDate(OpeningDay openingDay, DateTimeZone zone) {
    boolean allDay = openingDay.getAllDay();
    String date = openingDay.getDate();

    LocalDate localDate = LocalDate.parse(date, DATE_TIME_FORMATTER);
    if (allDay) {
      return localDate.toDateTime(END_OF_A_DAY, zone);
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();
      if (openingHours.isEmpty()) {
        return localDate.toDateTime(END_OF_A_DAY, zone);
      } else {
        OpeningHour openingHour = openingHours.get(openingHours.size() - 1);
        LocalTime endTime = LocalTime.parse(openingHour.getEndTime());
        return localDate.toDateTime(endTime, zone);
      }
    }
  }

  /**
   * Find the time of the end of the period by taking into account the time shift
   */
  public static LocalTime findEndTimeOfOpeningPeriod(List<OpeningHour> openingHoursList, LocalTime time) {
    LocalTime endTimePeriod = LocalTime.parse(openingHoursList.get(openingHoursList.size() - 1).getEndTime());
    if (time.isAfter(endTimePeriod)) {
      return endTimePeriod;
    }

    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = LocalTime.parse(openingHoursList.get(i).getStartTime());
      LocalTime endTimeFirst = LocalTime.parse(openingHoursList.get(i).getEndTime());
      LocalTime startTimeSecond = LocalTime.parse(openingHoursList.get(i + 1).getStartTime());
      if (time.isAfter(startTimeFirst) && time.isBefore(startTimeSecond)) {
        return endTimeFirst;
      }
    }
    return LocalTime.parse(openingHoursList.get(0).getEndTime());
  }
}
