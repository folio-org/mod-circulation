package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.PeriodUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.List;

public abstract class ClosedLibraryStrategy {

  public static final String DATE_TIME_FORMAT = "yyyy-MM-dd'Z'";
  public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern(DATE_TIME_FORMAT);
  public static final LocalTime END_OF_A_DAY = new LocalTime(23, 59, 59);


  private final LoanPolicyPeriod loanPeriod;

  protected ClosedLibraryStrategy(LoanPolicyPeriod loanPeriod) {
    this.loanPeriod = loanPeriod;
  }

  public static ClosedLibraryStrategy determineClosedLibraryStrategy(LoanPolicy loanPolicy, DateTime startDate) {
    DueDateManagement dueDateManagement = loanPolicy.getDueDateManagement();
    LoanPolicyPeriod loanPeriod = loanPolicy.getPeriodInterval();
    int periodDuration = loanPolicy.getPeriodDuration();
    LoanPolicyPeriod offsetInterval = loanPolicy.getOffsetPeriodInterval();
    int offsetDuration = loanPolicy.getOffsetPeriodDuration();

    switch (dueDateManagement) {
      case MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY:
        return new EndOfPreviousDayStrategy(loanPeriod);
      case MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY:
        return new EndOfNextOpenDayStrategy(loanPeriod);
      case MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS:
        return new EndOfCurrentHoursStrategy(loanPeriod, periodDuration, startDate);
      case MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS:
        return new StartOfNextOpenHoursStrategy(loanPeriod, periodDuration,
          offsetInterval, offsetDuration, startDate);
      case KEEP_THE_CURRENT_DUE_DATE:
      case KEEP_THE_CURRENT_DUE_DATE_TIME:
      default:
        return new KeepCurrentStrategy(loanPeriod);
    }
  }

  protected abstract DateTime calculateIfClosed(DateTime requestedDate, AdjustingOpeningDays adjustingOpeningDays);

  public DateTime calculateDueDate(DateTime requestedDate, AdjustingOpeningDays adjustingOpeningDays) {
    if (requestedDateTimeIsOpen(requestedDate, adjustingOpeningDays.getRequestedDay())) {
      return requestedDate;
    }
    return calculateIfClosed(requestedDate, adjustingOpeningDays);
  }

  private boolean requestedDateTimeIsOpen(DateTime requestedDate, OpeningDay requestedDay) {
    if (!requestedDay.getOpen()) {
      return false;
    }
    if (LoanPolicyPeriod.isShortTermLoans(loanPeriod)) {
      return PeriodUtil.isDateTimeWithDurationInsideDay(
        requestedDay,
        requestedDate.toLocalTime());
    }
    return true;
  }

  protected DateTime getTermDueDate(OpeningDay openingDay) {
    boolean allDay = openingDay.getAllDay();
    String date = openingDay.getDate();

    LocalDate localDate = LocalDate.parse(date, DATE_TIME_FORMATTER);
    if (allDay) {
      return getDateTimeZoneRetain(localDate.toDateTime(END_OF_A_DAY));
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();
      if (openingHours.isEmpty()) {
        return getDateTimeZoneRetain(localDate.toDateTime(END_OF_A_DAY));
      } else {
        OpeningHour openingHour = openingHours.get(openingHours.size() - 1);
        LocalTime endTime = LocalTime.parse(openingHour.getEndTime());
        return getDateTimeZoneRetain(localDate.toDateTime(endTime));
      }
    }
  }

  /**
   * Get DateTime in a specific zone
   */
  protected DateTime getDateTimeZoneRetain(DateTime dateTime) {
    return dateTimeWrapper(dateTime)
      .withZoneRetainFields(DateTimeZone.UTC);
  }


  protected DateTime dateTimeWrapper(DateTime dateTime) {
    return new DateTime(dateTime.toString());
  }

  protected DateTime calculateNewInitialDueDate(DateTime newDueDate) {
    return newDueDate.withZoneRetainFields(DateTimeZone.UTC);
  }

  /**
   * Find the time of the end of the period by taking into account the time shift
   */
  protected LocalTime findEndTimeOfOpeningPeriod(List<OpeningHour> openingHoursList, LocalTime time) {
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
