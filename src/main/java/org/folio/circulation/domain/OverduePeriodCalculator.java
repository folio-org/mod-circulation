package org.folio.circulation.domain;

import org.apache.commons.lang3.ObjectUtils;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;
import static org.joda.time.DateTimeConstants.MINUTES_PER_DAY;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;
import static org.joda.time.DateTimeConstants.MINUTES_PER_WEEK;
import static org.joda.time.Minutes.minutesBetween;

public class OverduePeriodCalculator {
  private static final int ZERO_MINUTES = 0;
  static final int MINUTES_PER_MONTH = MINUTES_PER_DAY * 31; //44640

  private OverduePeriodCalculator() {
    throw new UnsupportedOperationException("Do not instantiate!");
  }

  public static CompletableFuture<Result<Integer>> countMinutes(Loan loan, DateTime systemTime, Clients clients) {
    DateTime dueDate = loan.getDueDate();
    if (dueDate == null || dueDate.isAfter(systemTime)) {
      return completedFuture(succeeded(ZERO_MINUTES));
    }

    int gracePeriodMinutes = getGracePeriodMinutes(loan);
    return getOverdueMinutes(loan, systemTime, clients)
        .thenApply(r -> r.next(om -> adjustOverdueWithGracePeriod(loan, om, gracePeriodMinutes)));
  }

  private static int getGracePeriodMinutes(Loan loan) {
    LoanPolicy loanPolicy = loan.getLoanPolicy();
    if (loanPolicy == null) {
      return ZERO_MINUTES;
    }

    int duration = loanPolicy.getGracePeriodDuration();
    switch (loanPolicy.getGracePeriodInterval()) {
    case MONTHS:
      return duration * MINUTES_PER_MONTH;
    case WEEKS:
      return duration * MINUTES_PER_WEEK;
    case DAYS:
      return duration * MINUTES_PER_DAY;
    case HOURS:
      return duration * MINUTES_PER_HOUR;
    case INCORRECT:
      return ZERO_MINUTES;
    case MINUTES:
    default:
      return duration;
    }
  }

  private static CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan, DateTime systemTime, Clients clients) {
    OverdueFinePolicy overdueFinePolicy = loan.getOverdueFinePolicy();
    if (overdueFinePolicy == null || overdueFinePolicy.getCountClosed() == null) {
      return completedFuture(succeeded(ZERO_MINUTES));
    }

    DateTime dueDate = loan.getDueDate();
    boolean shouldCountClosed = overdueFinePolicy.getCountClosed();
    if (shouldCountClosed) {
      int overdueMinutes = minutesBetween(dueDate, systemTime).getMinutes();
      return completedFuture(succeeded(overdueMinutes));
    } else {
      return new CalendarRepository(clients)
        .fetchOpeningPeriodsBetweenDates(loan.getCheckoutServicePointId(), dueDate, systemTime, false)
      .thenApply(r -> r.next(OverduePeriodCalculator::getOpeningDaysDurationMinutes));
    }
  }

  private static Result<Integer> getOpeningDaysDurationMinutes(List<OpeningPeriod> openingDays) {
    return succeeded(
      openingDays.stream()
        .map(OpeningPeriod::getOpeningDay)
        .mapToInt(day -> day.getAllDay() ? MINUTES_PER_DAY : getOpeningDayDurationMinutes(day))
        .sum()
    );
  }

  private static int getOpeningDayDurationMinutes(OpeningDay openingDay) {
    return openingDay.getOpeningHour()
      .stream()
      .mapToInt(OverduePeriodCalculator::getOpeningHourDurationMinutes)
      .sum();
  }

  private static int getOpeningHourDurationMinutes(OpeningHour openingHour) {
    LocalTime startTime = openingHour.getStartTime();
    LocalTime endTime = openingHour.getEndTime();
    if (ObjectUtils.allNotNull(startTime, endTime) && endTime.isAfter(startTime)) {
      return getMinutesOfDay(endTime) - getMinutesOfDay(startTime);
    }
    return ZERO_MINUTES;
  }

  private static int getMinutesOfDay(LocalTime time) {
    return time.getHourOfDay() * MINUTES_PER_HOUR + time.getMinuteOfHour();
  }

  private static Result<Integer> adjustOverdueWithGracePeriod(Loan loan, int overdueMinutes, int gracePeriodMinutes) {
    int result = shouldIgnoreGracePeriod(loan)
      ? overdueMinutes
      : Math.max(overdueMinutes - gracePeriodMinutes, ZERO_MINUTES);

    return Result.succeeded(result);
  }

  private static boolean shouldIgnoreGracePeriod(Loan loan) {
    return loan.wasDueDateChangedByRecall()
      && loan.getOverdueFinePolicy() != null
      && loan.getOverdueFinePolicy().getIgnoreGracePeriodForRecalls();
  }

}
