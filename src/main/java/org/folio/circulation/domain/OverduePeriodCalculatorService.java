package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.Minutes.minutesBetween;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.Period;

public class OverduePeriodCalculatorService {
  private static final int ZERO_MINUTES = 0;

  private final CalendarRepository calendarRepository;
  private final LoanPolicyRepository loanPolicyRepository;

  public OverduePeriodCalculatorService(CalendarRepository calendarRepository,
    LoanPolicyRepository loanPolicyRepository) {

    this.calendarRepository = calendarRepository;
    this.loanPolicyRepository = loanPolicyRepository;
  }

  public CompletableFuture<Result<Integer>> getMinutes(Loan loan, DateTime systemTime) {
    final Boolean shouldCountClosedPeriods = loan.getOverdueFinePolicy().getCountPeriodsWhenServicePointIsClosed();

    if (preconditionsAreMet(loan, systemTime, shouldCountClosedPeriods)) {
      return completedFuture(loan)
        .thenComposeAsync(loanPolicyRepository::lookupPolicy)
        .thenApply(r -> r.map(loan::withLoanPolicy))
        .thenCompose(r -> r.after(l -> getOverdueMinutes(l, systemTime, shouldCountClosedPeriods)
            .thenApply(flatMapResult(om -> adjustOverdueWithGracePeriod(l, om)))));
    }

    return completedFuture(succeeded(ZERO_MINUTES));
  }

  boolean preconditionsAreMet(Loan loan, DateTime systemTime, Boolean shouldCountClosedPeriods) {
    return shouldCountClosedPeriods != null && loan.isOverdue(systemTime);
  }

  CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan, DateTime systemTime, boolean shouldCountClosedPeriods) {
    return shouldCountClosedPeriods || getItemLocationPrimaryServicePoint(loan) == null
      ? minutesOverdueIncludingClosedPeriods(loan, systemTime)
      : minutesOverdueExcludingClosedPeriods(loan, systemTime);
  }

  private CompletableFuture<Result<Integer>> minutesOverdueIncludingClosedPeriods(Loan loan, DateTime systemTime) {
    int overdueMinutes = minutesBetween(loan.getDueDate(), systemTime).getMinutes();
    return completedFuture(succeeded(overdueMinutes));
  }

  private CompletableFuture<Result<Integer>> minutesOverdueExcludingClosedPeriods(Loan loan, DateTime returnDate) {
    DateTime dueDate = loan.getDueDate();
    String itemLocationPrimaryServicePoint = getItemLocationPrimaryServicePoint(loan).toString();
    return calendarRepository
      .fetchOpeningDaysBetweenDates(itemLocationPrimaryServicePoint, dueDate, returnDate, false)
      .thenApply(r -> r.next(openingDays -> getOpeningDaysDurationMinutes(
        openingDays, dueDate.toLocalDateTime(), returnDate.toLocalDateTime())));
  }

  Result<Integer> getOpeningDaysDurationMinutes(
    Collection<OpeningDay> openingDays, LocalDateTime dueDate, LocalDateTime returnDate) {

    return succeeded(
      openingDays.stream()
        .mapToInt(day -> getOpeningDayDurationMinutes(day, dueDate, returnDate))
        .sum());
  }

  private int getOpeningDayDurationMinutes(
    OpeningDay openingDay, LocalDateTime dueDate, LocalDateTime systemTime) {

    DateTime datePart = openingDay.getDayWithTimeZone();

    return openingDay.getOpeningHour()
      .stream()
      .mapToInt(openingHour -> getOpeningHourDurationMinutes(
        openingHour, datePart, dueDate, systemTime))
      .sum();
  }

  private int getOpeningHourDurationMinutes(OpeningHour openingHour,
    DateTime datePart, LocalDateTime dueDate, LocalDateTime returnDate) {

    if (allNotNull(datePart, dueDate, openingHour.getStartTime(), openingHour.getEndTime())) {

      LocalDateTime startTime =  datePart.withTime(openingHour.getStartTime())
        .withZone(UTC).toLocalDateTime();
      LocalDateTime endTime = datePart.withTime(openingHour.getEndTime())
        .withZone(UTC).toLocalDateTime();

      if (dueDate.isAfter(startTime) && dueDate.isBefore(endTime)) {
        startTime = dueDate;
      }

      if (returnDate.isAfter(startTime) && returnDate.isBefore(endTime)) {
        endTime = returnDate;
      }

      if (endTime.isAfter(startTime) && endTime.isAfter(dueDate)
        && startTime.isBefore(returnDate)) {

        return calculateDiffInMinutes(startTime, endTime);
      }
    }

    return ZERO_MINUTES;
  }

  private int calculateDiffInMinutes(LocalDateTime start, LocalDateTime end) {
    Period period = new Period(start, end);
    return period.getHours() * MINUTES_PER_HOUR + period.getMinutes();
  }

  Result<Integer> adjustOverdueWithGracePeriod(Loan loan, int overdueMinutes) {
    int result;

    if (shouldIgnoreGracePeriod(loan)) {
      result = overdueMinutes;
    }
    else {
      result = overdueMinutes > getGracePeriodMinutes(loan) ? overdueMinutes : ZERO_MINUTES;
    }

    return Result.succeeded(result);
  }

  private boolean shouldIgnoreGracePeriod(Loan loan) {
    if (!loan.wasDueDateChangedByRecall()) {
      return false;
    }

    Boolean ignoreGracePeriodForRecalls = loan.getOverdueFinePolicy()
      .getIgnoreGracePeriodForRecalls();

    if (ignoreGracePeriodForRecalls == null) {
      return true;
    }

    return ignoreGracePeriodForRecalls;
  }

  private int getGracePeriodMinutes(Loan loan) {
    return loan.getLoanPolicy().getGracePeriod().toMinutes();
  }

  private UUID getItemLocationPrimaryServicePoint(Loan loan) {
    return loan.getItem().getLocation().getPrimaryServicePointId();
  }
}
