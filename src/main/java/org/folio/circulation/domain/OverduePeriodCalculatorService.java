package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isWithinMillis;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.support.results.Result;

public class OverduePeriodCalculatorService {
  private static final int ZERO_MINUTES = 0;
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CalendarRepository calendarRepository;
  private final LoanPolicyRepository loanPolicyRepository;

  public OverduePeriodCalculatorService(CalendarRepository calendarRepository,
    LoanPolicyRepository loanPolicyRepository) {

    this.calendarRepository = calendarRepository;
    this.loanPolicyRepository = loanPolicyRepository;
  }

  public CompletableFuture<Result<Integer>> getMinutes(Loan loan, ZonedDateTime systemTime, ZoneId zoneId) {
    log.debug("getMinutes:: parameters loan: {}, systemTime: {}", () -> loan, () -> systemTime);
    final Boolean shouldCountClosedPeriods = loan.getOverdueFinePolicy().getCountPeriodsWhenServicePointIsClosed();

    if (preconditionsAreMet(loan, systemTime, shouldCountClosedPeriods)) {
      log.info("getMinutes:: preconditions must be included");

      return completedFuture(loan)
        .thenComposeAsync(loanPolicyRepository::lookupPolicy)
        .thenApply(r -> r.map(loan::withLoanPolicy))
        .thenCompose(r -> r.after(l -> getOverdueMinutes(l, systemTime, shouldCountClosedPeriods, zoneId)
            .thenApply(flatMapResult(om -> adjustOverdueWithGracePeriod(l, om)))));
    }

    return completedFuture(succeeded(ZERO_MINUTES));
  }

  boolean preconditionsAreMet(Loan loan, ZonedDateTime systemTime, Boolean shouldCountClosedPeriods) {
    return shouldCountClosedPeriods != null && loan.isOverdue(systemTime);
  }

  CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan, ZonedDateTime systemTime,
    boolean shouldCountClosedPeriods, ZoneId zoneId) {

    return shouldCountClosedPeriods || getItemLocationPrimaryServicePoint(loan) == null
      ? minutesOverdueIncludingClosedPeriods(loan, systemTime)
      : minutesOverdueExcludingClosedPeriods(loan, systemTime, zoneId);
  }

  private CompletableFuture<Result<Integer>> minutesOverdueIncludingClosedPeriods(Loan loan,
    ZonedDateTime systemTime) {

    log.debug("minutesOverdueIncludingClosedPeriods:: parameters loan: {}, systemTime: {}",
      () -> loan, () -> systemTime);
    int overdueMinutes = calculateDiffInMinutes(loan.getDueDate(), systemTime);

    return completedFuture(succeeded(overdueMinutes));
  }

  private CompletableFuture<Result<Integer>> minutesOverdueExcludingClosedPeriods(Loan loan,
    ZonedDateTime returnDate, ZoneId zoneId) {

    log.debug("minutesOverdueExcludingClosedPeriods:: parameters loan: {}, returnDate: {}",
      () -> loan, () -> returnDate);
    ZonedDateTime dueDate = loan.getDueDate();
    String itemLocationPrimaryServicePoint = getItemLocationPrimaryServicePoint(loan).toString();

    return calendarRepository
      .fetchOpeningDaysBetweenDates(itemLocationPrimaryServicePoint, dueDate, returnDate, zoneId)
      .thenApply(r -> r.next(openingDays -> getOpeningDaysDurationMinutes(
        openingDays, dueDate, returnDate)));
  }

  Result<Integer> getOpeningDaysDurationMinutes(Collection<OpeningDay> openingDays,
    ZonedDateTime dueDate, ZonedDateTime returnDate) {

    log.debug("getOpeningDaysDurationMinutes:: parameters openingDays: {}, " +
      "dueDate: {}, returnDate: {}", () -> collectionAsString(openingDays),
      () -> dueDate, () -> returnDate);

    return succeeded(
      openingDays.stream()
        .filter(OpeningDay::isOpen)
        .mapToInt(day -> getOpeningDayDurationMinutes(day, dueDate, returnDate))
        .sum());
  }

  private int getOpeningDayDurationMinutes(OpeningDay openingDay, ZonedDateTime dueDate,
    ZonedDateTime systemTime) {

    log.debug("getOpeningDayDurationMinutes:: parameters openingDay: {}, " +
        "dueDate: {}, returnDate: {}", () -> openingDay, () -> dueDate, () -> systemTime);
    ZonedDateTime datePart = openingDay.getDayWithTimeZone();

    return openingDay.getOpenings()
      .stream()
      .mapToInt(openingHour -> getOpeningHourDurationMinutes(
        openingHour, datePart, dueDate, systemTime))
      .sum();
  }

  private int getOpeningHourDurationMinutes(OpeningHour openingHour,
    ZonedDateTime datePart, ZonedDateTime dueDate, ZonedDateTime returnDate) {

    log.debug("getOpeningHourDurationMinutes:: parameters openingHour: {}, " +
      "datePart: {}, dueDate: {}, returnDate: {}", () -> openingHour, () -> datePart,
      () -> dueDate, () -> returnDate);

    if (allNotNull(datePart, dueDate, openingHour.getStartTime(), openingHour.getEndTime())) {
      final LocalDate date = datePart.toLocalDate();
      ZonedDateTime startTime = ZonedDateTime.of(date, openingHour.getStartTime(), datePart.getZone());
      ZonedDateTime endTime = ZonedDateTime.of(date, openingHour.getEndTime(), datePart.getZone());

      if (isWithinMillis(dueDate, startTime, endTime)) {
        log.info("getOpeningHourDurationMinutes:: dueDate is within startTime and endTime");
        startTime = dueDate;
      }

      if (isWithinMillis(returnDate, startTime, endTime)) {
        log.info("getOpeningHourDurationMinutes:: returnDate is within startTime and endTime");
        endTime = returnDate;
      }

      if (isAfterMillis(endTime, startTime) && isAfterMillis(endTime, dueDate)
        && isBeforeMillis(startTime, returnDate)) {
        log.info("getOpeningHourDurationMinutes:: calculating difference in minutes");
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
    log.debug("adjustOverdueWithGracePeriod:: parameters loan: {}, overdueMinutes: {}",
      () -> loan, () -> overdueMinutes);
    int result;

    if (shouldIgnoreGracePeriod(loan)) {
      log.info("adjustOverdueWithGracePeriod:: grace period should be ignored");
      result = overdueMinutes;
    }
    else {
      result = overdueMinutes > getGracePeriodMinutes(loan) ? overdueMinutes : ZERO_MINUTES;
    }
    log.info("adjustOverdueWithGracePeriod:: result: {}", result);

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
