package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Hours;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.List;
import java.util.Objects;

import static org.folio.circulation.support.PeriodUtil.getStartAndEndTime;
import static org.folio.circulation.support.PeriodUtil.getTimeShift;
import static org.folio.circulation.support.PeriodUtil.isDateTimeWithDurationInsideDay;
import static org.folio.circulation.support.PeriodUtil.isInPeriodOpeningDay;

public class BeginningOfNextOpenHoursStrategy implements ClosedLibraryStrategy {

  private final int duration;
  private final LoanPolicyPeriod offsetInterval;
  private final int offsetDuration;
  private final LoanPolicyPeriod loanPeriod;
  private final DateTime startDate;
  private final DateTimeZone zone;

  public BeginningOfNextOpenHoursStrategy(
    LoanPolicyPeriod loanPeriod, int duration,
    LoanPolicyPeriod offsetInterval,
    int offsetDuration, DateTime startDate,
    DateTimeZone zone) {
    this.loanPeriod = loanPeriod;
    this.duration = duration;
    this.offsetInterval = offsetInterval;
    this.offsetDuration = offsetDuration;
    this.startDate = startDate;
    this.zone = zone;
  }

  @Override
  public DateTime calculateDueDate(DateTime requestedDate, AdjustingOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    OpeningDay prevOpeningDay = openingDays.getPreviousDay();
    OpeningDay currentOpeningDay = openingDays.getRequestedDay();
    OpeningDay nextOpeningDay = openingDays.getNextDay();


    if (currentOpeningDay.getOpen()) {
      if (currentOpeningDay.getAllDay()) {
        return requestedDate;
      }

    }


    if (!currentOpeningDay.getOpen()) {
      return getDateTimeNextOrPrevOpeningDay(prevOpeningDay, nextOpeningDay);
    }

    LocalDate dateOfCurrentDay = currentOpeningDay.getDate();
    LocalTime timeOfCurrentDay = startDate.toLocalTime();
    LocalTime timeShift = getTimeShift(timeOfCurrentDay, loanPeriod, duration);

    if (isDateTimeWithDurationInsideDay(currentOpeningDay, timeShift)) {
      return getDateTimeInsideOpeningDay(currentOpeningDay, dateOfCurrentDay, timeShift);
    }

    // Exception case when dateTime is outside the period
    LocalTime[] startAndEndTime = getStartAndEndTime(currentOpeningDay.getOpeningHour());
    LocalTime startTime = startAndEndTime[0];

    if (timeShift.isBefore(startTime)) {
      return getStartDateTimeOfOpeningDay(currentOpeningDay, dateOfCurrentDay);
    }

    LocalDate dateOfNextDay = nextOpeningDay.getDate();
    return getStartDateTimeOfOpeningDay(nextOpeningDay, dateOfNextDay);
  }


  /**
   * Get `dateTime` of the next day or the previous one if the next day is closed
   * <p>
   * An exceptional scenario is possible when `dateTime` falls on the limit value of the period when the library is closed.
   * In this case, we cannot know the next or current open day and we will be guided to the previous one.
   */
  private DateTime getDateTimeNextOrPrevOpeningDay(OpeningDay prevOpeningDay, OpeningDay nextOpeningDay) {
    return nextOpeningDay.getOpen()
      ? getDateTimeOfOpeningDay(nextOpeningDay)
      : getDateTimeOfOpeningDay(prevOpeningDay);
  }

  private DateTime getDateTimeOfOpeningDay(OpeningDay openingDay) {
    LocalDate date = openingDay.getDate();
    return getStartDateTimeOfOpeningDay(openingDay, date);
  }

  /**
   * Get the start date of the period or the beginning of the day for the day where the day is open all day
   */
  private DateTime getStartDateTimeOfOpeningDay(OpeningDay openingDay, LocalDate date) {
    if (openingDay.getAllDay()) {
      return calculateOffset(openingDay, date, LocalTime.MIDNIGHT);
    }

    List<OpeningHour> openingHoursList = openingDay.getOpeningHour();
    LocalTime startTime = openingHoursList.get(0).getStartTime();
    return calculateOffset(openingDay, date, startTime);
  }

  /**
   * Get the dateTime inside the period or within the opening day if the day is open all day
   * If `timeShift` is not found then return the start of day or period
   */
  private DateTime getDateTimeInsideOpeningDay(OpeningDay openingDay, LocalDate date,
                                               LocalTime timeShift) {
    if (openingDay.getAllDay()) {
      return date.toDateTime(timeShift, zone);
    }

    List<OpeningHour> openingHoursList = openingDay.getOpeningHour();
    if (isInPeriodOpeningDay(openingHoursList, timeShift)) {
      return date.toDateTime(timeShift, zone);
    }

    LocalTime startTimeOfNextPeriod = findStartTimeOfOpeningPeriod(openingHoursList, timeShift);
    return calculateOffset(openingDay, date, startTimeOfNextPeriod);
  }

  /**
   * Get the dateTime inside the period or within the opening day if the day is open all day
   * If `timeShift` is not found then return the end of day or period
   */
  private DateTime calculateOffset(OpeningDay openingDay, LocalDate date, LocalTime time) {

    DateTime dateTime = date.toDateTime(time, zone);
    List<OpeningHour> openingHours = openingDay.getOpeningHour();
    int offset = determineOffsetDurationWithinDay(dateTime);
    switch (offsetInterval) {
      case HOURS:
        if (openingDay.getAllDay()) {
          return dateTime.plusHours(offset);
        }

        LocalTime offsetTime = time.plusHours(offset);
        return getDateTimeOffsetInPeriod(openingHours, date, offsetTime);
      case MINUTES:
        if (openingDay.getAllDay()) {
          return dateTime.plusMinutes(offset);
        }

        offsetTime = time.plusMinutes(offset);
        return getDateTimeOffsetInPeriod(openingHours, date, offsetTime);
      default:
        return dateTime;
    }
  }

  private int determineOffsetDurationWithinDay(DateTime dateTime) {

    if (LoanPolicyPeriod.MINUTES == offsetInterval) {
      return offsetDuration;
    }

    LocalDate date = dateTime.toLocalDate();
    LocalTime time = dateTime.toLocalTime();

    LocalDate offsetDate = dateTime.plusHours(offsetDuration).toLocalDate();
    return date.isEqual(offsetDate)
      ? offsetDuration
      : Hours.hoursBetween(time, ClosedLibraryStrategyUtils.END_OF_A_DAY).getHours();
  }

  private DateTime getDateTimeOffsetInPeriod(List<OpeningHour> openingHour, LocalDate date, LocalTime offsetTime) {
    if (isInPeriodOpeningDay(openingHour, offsetTime)) {
      return date.toDateTime(offsetTime, zone);
    }

    LocalTime endTimeOfPeriod = ClosedLibraryStrategyUtils.findEndTimeOfOpeningPeriod(openingHour, offsetTime);
    return date.toDateTime(endTimeOfPeriod, zone);
  }

  /**
   * Find earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  private LocalTime findStartTimeOfOpeningPeriod(List<OpeningHour> openingHoursList, LocalTime time) {
    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = openingHoursList.get(i).getStartTime();
      LocalTime startTimeSecond = openingHoursList.get(i + 1).getStartTime();
      if (time.isAfter(startTimeFirst) && time.isBefore(startTimeSecond)) {
        return startTimeSecond;
      }
    }
    return time;
  }

}
