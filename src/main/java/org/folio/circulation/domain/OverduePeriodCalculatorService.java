package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.Minutes.minutesBetween;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
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

  public CompletableFuture<Result<Integer>> getMinutes(LoanToChargeOverdueFine loanToCharge) {
    return loanPolicyRepository.lookupPolicy(loanToCharge.getLoan())
      .thenApply(r -> r.map(loanToCharge::withLoanPolicy))
      .thenCompose(r -> r.after(l -> getOverdueMinutes(loanToCharge)
        .thenApply(flatMapResult(om -> adjustOverdueWithGracePeriod(loanToCharge.getLoan(), om)))));
  }

  CompletableFuture<Result<Integer>> getOverdueMinutes(LoanToChargeOverdueFine loanToCharge) {
    return loanToCharge.shouldCountClosedPeriods()
      || !loanToCharge.hasItemLocationPrimaryServicePoint()
      ? minutesOverdueIncludingClosedPeriods(loanToCharge)
      : minutesOverdueExcludingClosedPeriods(loanToCharge);
  }

  private CompletableFuture<Result<Integer>> minutesOverdueIncludingClosedPeriods(
    LoanToChargeOverdueFine loanToCharge) {

    int overdueMinutes = minutesBetween(loanToCharge.getDueDate(),
      loanToCharge.getReturnDate()).getMinutes();

    return completedFuture(succeeded(overdueMinutes));
  }

  private CompletableFuture<Result<Integer>> minutesOverdueExcludingClosedPeriods(
    LoanToChargeOverdueFine loanToCharge) {

    final String itemPrimaryServicePoint = loanToCharge.getItemPrimaryServicePoint();
    final DateTime dueDate = loanToCharge.getDueDate();
    final DateTime returnDate  = loanToCharge.getReturnDate();

    return calendarRepository
      .fetchOpeningDaysBetweenDates(itemPrimaryServicePoint, dueDate, returnDate, false)
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
}
