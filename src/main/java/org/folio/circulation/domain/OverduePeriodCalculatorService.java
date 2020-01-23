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
import static org.folio.circulation.support.ResultBinding.flatMapResult;
import static org.joda.time.DateTimeConstants.MINUTES_PER_DAY;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;
import static org.joda.time.DateTimeConstants.MINUTES_PER_WEEK;
import static org.joda.time.Minutes.minutesBetween;

public class OverduePeriodCalculatorService {
  private static final int ZERO_MINUTES = 0;
  private static final int MINUTES_PER_MONTH = 44640;

  private final CalendarRepository calendarRepository;

  public OverduePeriodCalculatorService(CalendarRepository calendarRepository) {
    this.calendarRepository = calendarRepository;
  }

  public static OverduePeriodCalculatorService using(Clients clients) {
    return new OverduePeriodCalculatorService(new CalendarRepository(clients));
  }

  public CompletableFuture<Result<Integer>> getMinutes(Loan loan, DateTime systemTime) {
    if (preconditionsAreNotMet(loan, systemTime)) {
      return completedFuture(succeeded(ZERO_MINUTES));
    }
    return getOverdueMinutes(loan, systemTime)
        .thenApply(flatMapResult(om -> adjustOverdueWithGracePeriod(loan, om)));
  }

  private boolean preconditionsAreNotMet(Loan loan, DateTime systemTime) {
    DateTime dueDate = loan.getDueDate();
    OverdueFinePolicy overdueFinePolicy = loan.getOverdueFinePolicy();
    return dueDate == null
      || dueDate.isAfter(systemTime)
      || overdueFinePolicy.isUnknown()
      || overdueFinePolicy.getCountClosed() == null;
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan, DateTime systemTime) {
    DateTime dueDate = loan.getDueDate();
    boolean countClosed = loan.getOverdueFinePolicy().getCountClosed();
    if (countClosed) {
      int overdueMinutes = minutesBetween(dueDate, systemTime).getMinutes();
      return completedFuture(succeeded(overdueMinutes));
    } else {
      return calendarRepository
        .fetchOpeningPeriodsBetweenDates(loan.getCheckoutServicePointId(), dueDate, systemTime, false)
      .thenApply(r -> r.next(this::getOpeningDaysDurationMinutes));
    }
  }

  private Result<Integer> getOpeningDaysDurationMinutes(List<OpeningPeriod> openingDays) {
    return succeeded(
      openingDays.stream()
        .map(OpeningPeriod::getOpeningDay)
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

  private Result<Integer> adjustOverdueWithGracePeriod(Loan loan, int overdueMinutes) {
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
    LoanPolicy loanPolicy = loan.getLoanPolicy();
    if (loanPolicy.isUnknown()) {
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

}
