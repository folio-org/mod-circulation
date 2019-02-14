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
import java.util.function.BiPredicate;

import static org.folio.circulation.support.PeriodUtil.getStartAndEndTime;
import static org.folio.circulation.support.PeriodUtil.getTimeShift;
import static org.folio.circulation.support.PeriodUtil.isDateTimeWithDurationInsideDay;
import static org.folio.circulation.support.PeriodUtil.isInPeriodOpeningDay;

public class EndOfCurrentHoursStrategy implements ClosedLibraryStrategy {

  private final int duration;
  private final DateTime loanDate;
  private final LoanPolicyPeriod loanPeriod;
  private final BiPredicate<DateTime, AdjustingOpeningDays> libraryIsOpenPredicate;
  private final DateTimeZone zone;

  public EndOfCurrentHoursStrategy(
    LoanPolicyPeriod loanPeriod, int duration, DateTime loanDate,
    BiPredicate<DateTime, AdjustingOpeningDays> libraryIsOpenPredicate, DateTimeZone zone) {
    this.loanPeriod = loanPeriod;
    this.duration = duration;
    this.loanDate = loanDate;
    this.libraryIsOpenPredicate = libraryIsOpenPredicate;
    this.zone = zone;
  }

  @Override
  public DateTime calculateDueDate(DateTime requestedDate, AdjustingOpeningDays adjustingOpeningDays) {
    if (libraryIsOpenPredicate.test(requestedDate, adjustingOpeningDays)) {
      return requestedDate;
    }
    return getShortTermDueDateEndCurrentHours(adjustingOpeningDays);
  }

  private DateTime getShortTermDueDateEndCurrentHours(AdjustingOpeningDays adjustingOpeningDays) {
    OpeningDay prevOpeningDay = adjustingOpeningDays.getPreviousDay();
    OpeningDay currentOpeningDay = adjustingOpeningDays.getRequestedDay();

    if (!currentOpeningDay.getOpen()) {
      return ClosedLibraryStrategyUtils.getTermDueDate(prevOpeningDay, zone);
    }

    LocalDate dateOfCurrentDay = LocalDate.parse(currentOpeningDay.getDate(), ClosedLibraryStrategyUtils.DATE_TIME_FORMATTER);
    LocalTime timeOfCurrentDay = loanDate.toLocalTime();
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
      LocalDate dateOfPrevDay = LocalDate.parse(prevOpeningDay.getDate(), ClosedLibraryStrategyUtils.DATE_TIME_FORMATTER);
      LocalTime prevEndTime = getStartAndEndTime(prevOpeningDay.getOpeningHour())[1];
      return dateOfPrevDay.toDateTime(prevEndTime, zone);
    }

    return dateOfCurrentDay.toDateTime(timeShift, zone);
  }

}
