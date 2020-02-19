package org.folio.circulation.domain;

import org.apache.commons.lang3.ObjectUtils;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.flatMapResult;
import static org.joda.time.DateTimeConstants.MINUTES_PER_DAY;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;
import static org.joda.time.Minutes.minutesBetween;

public class OverduePeriodCalculatorService {
  private static final int ZERO_MINUTES = 0;

  private final CalendarRepository calendarRepository;

  public OverduePeriodCalculatorService(CalendarRepository calendarRepository) {
    this.calendarRepository = calendarRepository;
  }

  public CompletableFuture<Result<Integer>> getMinutes(Loan loan, DateTime systemTime) {
    final Boolean shouldCountClosedPeriods = loan.getOverdueFinePolicy().getCountPeriodsWhenServicePointIsClosed();

    if (preconditionsAreMet(loan, systemTime, shouldCountClosedPeriods)) {
      return getOverdueMinutes(loan, systemTime, shouldCountClosedPeriods)
        .thenApply(flatMapResult(om -> adjustOverdueWithGracePeriod(loan, om)));
    }

    return completedFuture(succeeded(ZERO_MINUTES));
  }

  boolean preconditionsAreMet(Loan loan, DateTime systemTime, Boolean shouldCountClosedPeriods) {
    return shouldCountClosedPeriods != null && loan.isOverdue(systemTime);
  }

  CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan, DateTime systemTime, boolean shouldCountClosedPeriods) {
    return shouldCountClosedPeriods
      ? minutesOverdueIncludingClosedPeriods(loan, systemTime)
      : minutesOverdueExcludingClosedPeriods(loan, systemTime);
  }

  private CompletableFuture<Result<Integer>> minutesOverdueIncludingClosedPeriods(Loan loan, DateTime systemTime) {
    int overdueMinutes = minutesBetween(loan.getDueDate(), systemTime).getMinutes();
    return completedFuture(succeeded(overdueMinutes));
  }

  private CompletableFuture<Result<Integer>> minutesOverdueExcludingClosedPeriods(Loan loan, DateTime systemTime) {
    return calendarRepository
      .fetchOpeningDaysBetweenDates(loan.getCheckoutServicePointId(), loan.getDueDate(), systemTime, false)
      .thenApply(r -> r.next(this::getOpeningDaysDurationMinutes));
  }

  Result<Integer> getOpeningDaysDurationMinutes(Collection<OpeningDay> openingDays) {
    return succeeded(
      openingDays.stream()
        .mapToInt(day -> day.getAllDay() ? MINUTES_PER_DAY : getOpeningDayDurationMinutes(day))
        .sum()
    );
  }

  private int getOpeningDayDurationMinutes(OpeningDay openingDay) {
    return openingDay.getOpeningHour()
      .stream()
      .mapToInt(this::getOpeningHourDurationMinutes)
      .sum();
  }

  private int getOpeningHourDurationMinutes(OpeningHour openingHour) {
    LocalTime startTime = openingHour.getStartTime();
    LocalTime endTime = openingHour.getEndTime();

    if (ObjectUtils.allNotNull(startTime, endTime) && endTime.isAfter(startTime)) {
      return getMinutesOfDay(endTime) - getMinutesOfDay(startTime);
    }

    return ZERO_MINUTES;
  }

  private int getMinutesOfDay(LocalTime time) {
    return time.getHourOfDay() * MINUTES_PER_HOUR + time.getMinuteOfHour();
  }

  Result<Integer> adjustOverdueWithGracePeriod(Loan loan, int overdueMinutes) {
    int result = shouldIgnoreGracePeriod(loan)
      ? overdueMinutes
      : Math.max(overdueMinutes - getGracePeriodMinutes(loan), ZERO_MINUTES);

    return Result.succeeded(result);
  }

  private boolean shouldIgnoreGracePeriod(Loan loan) {
    Boolean ignoreGracePeriodForRecalls = loan.getOverdueFinePolicy().getIgnoreGracePeriodForRecalls();

    return ignoreGracePeriodForRecalls != null
      && ignoreGracePeriodForRecalls
      && loan.wasDueDateChangedByRecall();
  }

  private int getGracePeriodMinutes(Loan loan) {
    return loan.getLoanPolicy().getGracePeriod().toMinutes();
  }

}
