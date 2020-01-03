package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.joda.time.Period;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

public class OverduePeriodCalculator {
  private static final int MINUTES_IN_HOUR = 60;
  private static final int MINUTES_IN_DAY = 1140;
  private static final int MINUTES_IN_WEEK = 10080;
  private static final int MINUTES_IN_MONTH = 44640;

  private CalendarRepository calendarRepository;

  public OverduePeriodCalculator(Clients clients) {
    this.calendarRepository = new CalendarRepository(clients);
  }

  public CompletableFuture<Result<Integer>> calculateOverdueMinutes(Loan loan) {
    int gracePeriodMinutes = getGracePeriodMinutes(loan);
    return getOverdueMinutes(loan)
        .thenApply(r -> r.next(overdueMinutes -> matchOverdueMinutesToGracePeriod(overdueMinutes, gracePeriodMinutes)));
  }

  private int getGracePeriodMinutes(Loan loan) {
    final LoanPolicy loanPolicy = loan.getLoanPolicy();
    int duration = loanPolicy.getGracePeriodDuration();
    if (duration <= 0 || shouldIgnoreGracePeriod(loan)) {
      return 0;
    }

    switch (loanPolicy.getGracePeriodInterval()) {
    case MINUTES:
      return duration;
    case HOURS:
      return duration * MINUTES_IN_HOUR;
    case DAYS:
      return duration * MINUTES_IN_DAY;
    case WEEKS:
      return duration * MINUTES_IN_WEEK;
    case MONTHS:
      return duration * MINUTES_IN_MONTH;
    default:
      return duration;
    }
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan) {
    final DateTime now = DateTime.now(DateTimeZone.UTC);
    if (loan.getOverdueFinePolicy().shouldCountClosed()) {
      int overdueMinutes = 0;
      if (loan.getDueDate().isBefore(now)) {
        overdueMinutes = (int) new Period(DateTime.now().minusDays(2), DateTime.now())
          .toStandardDuration()
          .getStandardMinutes();
      }
      return completedFuture(succeeded(overdueMinutes));
    } else {
      return calendarRepository.fetchOpeningPeriods(loan.getDueDate().toLocalDate(), now.toLocalDate(), loan.getCheckoutServicePointId())
      .thenApply(r -> r.next(this::getOverdueMinutes));
    }
  }

  private Result<Integer> getOverdueMinutes(List<OpeningDay> openingDays) {
    return succeeded(
      openingDays.stream()
        .map(OpeningDay::getOpeningHour)
        .flatMap(Collection::stream)
        .mapToInt(this::getOpeningMinutes)
        .sum()
    );
  }

  private int getOpeningMinutes(OpeningHour openingHour) {
    return getMinutes(openingHour.getEndTime()) - getMinutes(openingHour.getStartTime());
  }

  private int getMinutes(LocalTime time) {
    return time.getHourOfDay() * MINUTES_IN_HOUR + time.getMinuteOfHour();
  }

  private Result<Integer> matchOverdueMinutesToGracePeriod(int overdueMinutes, int gracePeriodMinutes) {
    int result = 0;
    if (overdueMinutes > gracePeriodMinutes) {
      result = overdueMinutes - gracePeriodMinutes;
    }
    return Result.succeeded(result);
  }

  private boolean shouldIgnoreGracePeriod(Loan loan) {
    return loan.wasDueDateChangedByRecall() 
        && loan.getOverdueFinePolicy().shouldIgnoreGracePeriodsForRecalls();
  }

}
