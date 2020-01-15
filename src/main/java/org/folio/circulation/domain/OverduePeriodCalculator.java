package org.folio.circulation.domain;

import org.apache.commons.lang3.ObjectUtils;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;
import static org.joda.time.DateTimeConstants.MINUTES_PER_DAY;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;
import static org.joda.time.DateTimeConstants.MINUTES_PER_WEEK;
import static org.joda.time.Minutes.minutesBetween;

public class OverduePeriodCalculator {
  static final int MINUTES_PER_MONTH = MINUTES_PER_DAY * 31; //44640

  public CompletableFuture<Result<Integer>> calculateOverdueMinutes(Loan loan, DateTime systemTime, CalendarRepository calendarRepository) {
    int gracePeriodMinutes = getGracePeriodMinutes(loan);
    return getOverdueMinutes(loan, systemTime, calendarRepository)
        .thenApply(r -> r.next(overdueMinutes -> adjustOverdueMinutesWithGracePeriod(overdueMinutes, gracePeriodMinutes)));
  }

  private int getGracePeriodMinutes(Loan loan) {
    LoanPolicy loanPolicy = loan.getLoanPolicy();
    if (loanPolicy == null) {
      return 0;
    }
    
    int duration = loanPolicy.getGracePeriodDuration();
    if (duration <= 0 || shouldIgnoreGracePeriod(loan)) {
      return 0;
    }

    switch (loanPolicy.getGracePeriodInterval()) {
    case MINUTES:
      return duration;
    case HOURS:
      return duration * MINUTES_PER_HOUR;
    case DAYS:
      return duration * MINUTES_PER_DAY;
    case WEEKS:
      return duration * MINUTES_PER_WEEK;
    case MONTHS:
      return duration * MINUTES_PER_MONTH;
    default:
      return 0;
    }
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan, DateTime systemTime, CalendarRepository calendarRepository) {
    final DateTime dueDate = loan.getDueDate();
//    final DateTime now = DateTime.now(DateTimeZone.UTC);
    if (loan.getOverdueFinePolicy().shouldCountClosed()) {
      int overdueMinutes = dueDate.isBefore(systemTime) ? minutesBetween(dueDate, systemTime).getMinutes() : 0;
      return completedFuture(succeeded(overdueMinutes));
    } else {
      return calendarRepository.fetchOpeningDaysBetweenDates(dueDate.toLocalDate(), systemTime.toLocalDate(), loan.getCheckoutServicePointId())
      .thenApply(r -> r.next(this::getOpeningDaysDurationMinutes));
    }
  }

  private Result<Integer> getOpeningDaysDurationMinutes(List<OpeningDay> openingDays) {
    return succeeded(
      openingDays.stream()
        .map(OpeningDay::getOpeningHour)
        .flatMap(Collection::stream)
        .mapToInt(this::getOpeningHourDurationMinutes)
        .sum()
    );
  }

  private int getOpeningHourDurationMinutes(OpeningHour openingHour) {
    final LocalTime startTime = openingHour.getStartTime();
    final LocalTime endTime = openingHour.getEndTime();
    if (ObjectUtils.allNotNull(startTime, endTime) && startTime.isBefore(endTime)) {
      return getMinutesOfDay(endTime) - getMinutesOfDay(startTime);
    }
    return 0;
  }

  private int getMinutesOfDay(LocalTime time) {
    return time.getHourOfDay() * MINUTES_PER_HOUR + time.getMinuteOfHour();
  }

  private Result<Integer> adjustOverdueMinutesWithGracePeriod(int overdueMinutes, int gracePeriodMinutes) {
    int result = 0;
    if (overdueMinutes > gracePeriodMinutes) {
      result = overdueMinutes - gracePeriodMinutes;
    }
    return Result.succeeded(result);
  }

  private boolean shouldIgnoreGracePeriod(Loan loan) {
    return loan.wasDueDateChangedByRecall()
      && loan.getOverdueFinePolicy() != null
      && loan.getOverdueFinePolicy().shouldIgnoreGracePeriodsForRecalls();
  }

}
