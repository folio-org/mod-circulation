package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.List;
import java.util.Objects;

import static org.folio.circulation.support.PeriodUtil.getStartAndEndTime;
import static org.folio.circulation.support.PeriodUtil.getTimeShift;
import static org.folio.circulation.support.PeriodUtil.isDateTimeWithDurationInsideDay;
import static org.folio.circulation.support.PeriodUtil.isInPeriodOpeningDay;

public class EndOfCurrentHoursStrategy implements ClosedLibraryStrategy {

  private final int duration;
  private final DateTime startDate;
  private final LoanPolicyPeriod loanPeriod;
  private final DateTimeZone zone;

  public EndOfCurrentHoursStrategy(
    LoanPolicyPeriod loanPeriod, int duration,
    DateTime startDate, DateTimeZone zone) {
    this.loanPeriod = loanPeriod;
    this.duration = duration;
    this.startDate = startDate;
    this.zone = zone;
  }

  @Override
  public DateTime calculateDueDate(DateTime requestedDate, AdjustingOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    OpeningDay prevOpeningDay = openingDays.getPreviousDay();
    OpeningDay currentOpeningDay = openingDays.getRequestedDay();

    if (!currentOpeningDay.getOpen()) {
      return ClosedLibraryStrategyUtils.getLatestClosingHours(prevOpeningDay, zone);
    }

    LocalDate dateOfCurrentDay = currentOpeningDay.getDate();
    LocalTime timeOfCurrentDay = startDate.toLocalTime();
    LocalTime timeShift = getTimeShift(timeOfCurrentDay, loanPeriod, duration);

    if (isDateTimeWithDurationInsideDay(currentOpeningDay, timeShift)) {
      return getDateTimeInsideOpeningDay(currentOpeningDay, dateOfCurrentDay, timeShift);
    }

    return getDateTimeOutsidePeriod(prevOpeningDay, currentOpeningDay, dateOfCurrentDay, timeShift);
  }

  private DateTime getDateTimeInsideOpeningDay(OpeningDay openingDay, LocalDate date,
                                               LocalTime timeShift) {
    if (openingDay.getAllDay()) {
      return date.toDateTime(timeShift, zone);
    }

    List<OpeningHour> openingHoursList = openingDay.getOpeningHour();
    if (isInPeriodOpeningDay(openingHoursList, timeShift)) {
      return date.toDateTime(timeShift, zone);
    }

    LocalTime endTime = ClosedLibraryStrategyUtils.findEndTimeOfOpeningPeriod(openingHoursList, timeShift);
    return date.toDateTime(endTime, zone);
  }

  private DateTime getDateTimeOutsidePeriod(OpeningDay prevOpeningDay, OpeningDay currentOpeningDay,
                                            LocalDate dateOfCurrentDay, LocalTime timeShift) {
    LocalTime[] startAndEndTime = getStartAndEndTime(currentOpeningDay.getOpeningHour());
    LocalTime startTime = startAndEndTime[0];
    LocalTime endTime = startAndEndTime[1];

    if (timeShift.isAfter(endTime)) {
      return dateOfCurrentDay.toDateTime(endTime, zone);
    }

    if (timeShift.isBefore(startTime)) {
      LocalDate dateOfPrevDay = prevOpeningDay.getDate();
      LocalTime prevEndTime = getStartAndEndTime(prevOpeningDay.getOpeningHour())[1];
      return dateOfPrevDay.toDateTime(prevEndTime, zone);
    }

    return dateOfCurrentDay.toDateTime(timeShift, zone);
  }

}
