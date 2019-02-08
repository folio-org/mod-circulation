package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.List;

import static org.folio.circulation.support.PeriodUtil.getStartAndEndTime;
import static org.folio.circulation.support.PeriodUtil.getTimeShift;
import static org.folio.circulation.support.PeriodUtil.isDateTimeWithDurationInsideDay;
import static org.folio.circulation.support.PeriodUtil.isInPeriodOpeningDay;

public class EndOfCurrentHoursStrategy extends ClosedLibraryStrategy {

  private final int duration;
  private final DateTime loanDate;
  private LoanPolicyPeriod loanPeriod;

  public EndOfCurrentHoursStrategy(LoanPolicyPeriod loanPeriod, int duration, DateTime loanDate) {
    super(loanPeriod);
    this.loanPeriod = loanPeriod;
    this.duration = duration;
    this.loanDate = loanDate;
  }


  @Override
  protected DateTime calculateIfClosed(DateTime requestedDate, AdjustingOpeningDays adjustingOpeningDays) {
    DateTime dateTime = getShortTermDueDateEndCurrentHours(adjustingOpeningDays);
    return calculateNewInitialDueDate(dateTime);
  }

  private DateTime getShortTermDueDateEndCurrentHours(AdjustingOpeningDays adjustingOpeningDays) {
    OpeningDay prevOpeningDay = adjustingOpeningDays.getPreviousDay();
    OpeningDay currentOpeningDay = adjustingOpeningDays.getRequestedDay();

    if (!currentOpeningDay.getOpen()) {
      return getTermDueDate(prevOpeningDay);
    }

    LocalDate dateOfCurrentDay = LocalDate.parse(currentOpeningDay.getDate(), DATE_TIME_FORMATTER);
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
      return dateTimeWrapper(date.toDateTime(timeShift));
    }

    List<OpeningHour> openingHoursList = openingDay.getOpeningHour();
    if (isInPeriodOpeningDay(openingHoursList, timeShift)) {
      return dateTimeWrapper(date.toDateTime(timeShift));
    }

    LocalTime endTime = findEndTimeOfOpeningPeriod(openingHoursList, timeShift);
    return dateTimeWrapper(date.toDateTime(endTime));
  }

  private DateTime getDateTimeOutsidePeriod(OpeningDay prevOpeningDay, OpeningDay currentOpeningDay,
                                            LocalDate dateOfCurrentDay, LocalTime timeShift) {
    LocalTime[] startAndEndTime = getStartAndEndTime(currentOpeningDay.getOpeningHour());
    LocalTime startTime = startAndEndTime[0];
    LocalTime endTime = startAndEndTime[1];

    if (timeShift.isAfter(endTime)) {
      return dateTimeWrapper(dateOfCurrentDay.toDateTime(endTime));
    }

    if (timeShift.isBefore(startTime)) {
      LocalDate dateOfPrevDay = LocalDate.parse(prevOpeningDay.getDate(), DATE_TIME_FORMATTER);
      LocalTime prevEndTime = getStartAndEndTime(prevOpeningDay.getOpeningHour())[1];
      return dateTimeWrapper(dateOfPrevDay.toDateTime(prevEndTime));
    }

    return dateTimeWrapper(dateOfCurrentDay.toDateTime(timeShift));
  }

}
