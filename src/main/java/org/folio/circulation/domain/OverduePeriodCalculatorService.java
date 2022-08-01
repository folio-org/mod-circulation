package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isWithinMillis;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.support.results.Result;

public class OverduePeriodCalculatorService {
  private static final int ZERO_MINUTES = 0;

  private final CalendarRepository calendarRepository;
  private final LoanPolicyRepository loanPolicyRepository;

  public OverduePeriodCalculatorService(CalendarRepository calendarRepository,
    LoanPolicyRepository loanPolicyRepository) {

    this.calendarRepository = calendarRepository;
    this.loanPolicyRepository = loanPolicyRepository;
  }

  public CompletableFuture<Result<Integer>> getMinutes(Loan loan, ZonedDateTime systemTime) {
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

  boolean preconditionsAreMet(Loan loan, ZonedDateTime systemTime, Boolean shouldCountClosedPeriods) {
    return shouldCountClosedPeriods != null && loan.isOverdue(systemTime);
  }

  CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan, ZonedDateTime systemTime, boolean shouldCountClosedPeriods) {
    return shouldCountClosedPeriods || getItemLocationPrimaryServicePoint(loan) == null
      ? minutesOverdueIncludingClosedPeriods(loan, systemTime)
      : minutesOverdueExcludingClosedPeriods(loan, systemTime);
  }

  private CompletableFuture<Result<Integer>> minutesOverdueIncludingClosedPeriods(Loan loan, ZonedDateTime systemTime) {
    int overdueMinutes = calculateDiffInMinutes(loan.getDueDate(), systemTime);
    return completedFuture(succeeded(overdueMinutes));
  }

  private CompletableFuture<Result<Integer>> minutesOverdueExcludingClosedPeriods(Loan loan, ZonedDateTime returnDate) {
    ZonedDateTime dueDate = loan.getDueDate();
    String itemLocationPrimaryServicePoint = getItemLocationPrimaryServicePoint(loan).toString();
    return calendarRepository
      .fetchOpeningDaysBetweenDates(itemLocationPrimaryServicePoint, dueDate, returnDate)
      .thenApply(r -> r.next(openingDays -> getOpeningDaysDurationMinutes(
        openingDays, dueDate, returnDate)));
  }

  Result<Integer> getOpeningDaysDurationMinutes(
    Collection<OpeningDay> openingDays, ZonedDateTime dueDate, ZonedDateTime returnDate) {

    return succeeded(
      openingDays.stream()
        .filter(OpeningDay::isOpen)
        .mapToInt(day -> getOpeningDayDurationMinutes(day, dueDate, returnDate))
        .sum());
  }

  private int getOpeningDayDurationMinutes(
    OpeningDay openingDay, ZonedDateTime dueDate, ZonedDateTime systemTime) {

    ZonedDateTime datePart = openingDay.getDayWithTimeZone();

    return openingDay.getOpeningHour()
      .stream()
      .mapToInt(openingHour -> getOpeningHourDurationMinutes(
        openingHour, datePart, dueDate, systemTime))
      .sum();
  }

  private int getOpeningHourDurationMinutes(OpeningHour openingHour,
    ZonedDateTime datePart, ZonedDateTime dueDate, ZonedDateTime returnDate) {

    if (allNotNull(datePart, dueDate, openingHour.getStartTime(), openingHour.getEndTime())) {
      final LocalDate date = datePart.toLocalDate();
      ZonedDateTime startTime = ZonedDateTime.of(date, openingHour.getStartTime(), datePart.getZone());
      ZonedDateTime endTime = ZonedDateTime.of(date, openingHour.getEndTime(), datePart.getZone());

      if (isWithinMillis(dueDate, startTime, endTime)) {
        startTime = dueDate;
      }

      if (isWithinMillis(returnDate, startTime, endTime)) {
        endTime = returnDate;
      }

      if (isAfterMillis(endTime, startTime) && isAfterMillis(endTime, dueDate)
        && isBeforeMillis(startTime, returnDate)) {
        return calculateDiffInMinutes(startTime, endTime);
      }
    }

    return ZERO_MINUTES;
  }

  private int calculateDiffInMinutes(ZonedDateTime start, ZonedDateTime end) {
    long startSeconds = start.truncatedTo(ChronoUnit.MINUTES).toEpochSecond();
    long endSeconds = end.truncatedTo(ChronoUnit.MINUTES).toEpochSecond();

    return (int) ((endSeconds - startSeconds) / 60);
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

  private long getGracePeriodMinutes(Loan loan) {
    return loan.getLoanPolicy().getGracePeriod().toMinutes();
  }

  private UUID getItemLocationPrimaryServicePoint(Loan loan) {
    return loan.getItem().getLocation().getPrimaryServicePointId();
  }
}
