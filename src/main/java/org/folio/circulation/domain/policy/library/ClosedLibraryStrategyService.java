package org.folio.circulation.domain.policy.library;

import org.folio.circulation.domain.CalendarRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.policy.FixedDueDateSchedules;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.PeriodUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ClosedLibraryStrategyService {

  public static ClosedLibraryStrategyService using(
    Clients clients, DateTime currentTime, boolean isRenewal) {
    return new ClosedLibraryStrategyService(new CalendarRepository(clients), currentTime, isRenewal);
  }

  private final CalendarRepository calendarRepository;
  private final DateTime currentTime;
  private final boolean isRenewal;

  public ClosedLibraryStrategyService(
    CalendarRepository calendarRepository, DateTime currentTime, boolean isRenewal) {
    this.calendarRepository = calendarRepository;
    this.currentTime = currentTime;
    this.isRenewal = isRenewal;
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> applyCLDDM(LoanAndRelatedRecords relatedRecords) {
    return CompletableFuture.completedFuture(HttpResult.succeeded(relatedRecords))
      .thenComposeAsync(r -> r.after(calendarRepository::lookupPeriod))
      .thenApply(r -> r.next(this::applyStrategy))
      .thenApply(r -> r.next(this::applyFixedDueDateLimit));
  }

  private HttpResult<LoanAndRelatedRecords> applyStrategy(LoanAndRelatedRecords relatedRecords) {
    if (relatedRecords.getAdjustingOpeningDays() == null) {
      return HttpResult.succeeded(relatedRecords);
    }
    DateTimeZone timeZone = relatedRecords.getTimeZone();
    ClosedLibraryStrategy strategy =
      ClosedLibraryStrategyUtils.determineClosedLibraryStrategy(
        relatedRecords.getLoanPolicy(),
        currentTime,
        timeZone);

    DateTime dueDate = relatedRecords.getLoan().getDueDate();
    HttpResult<DateTime> calculateDueDate =
      strategy.calculateDueDate(dueDate, relatedRecords.getAdjustingOpeningDays());
    return calculateDueDate.next(date -> {
      relatedRecords.getLoan().changeDueDate(date);
      return HttpResult.succeeded(relatedRecords);
    });
  }

  private HttpResult<LoanAndRelatedRecords> applyFixedDueDateLimit(LoanAndRelatedRecords relatedRecords) {
    if (relatedRecords.getAdjustingOpeningDays() == null) {
      return HttpResult.succeeded(relatedRecords);
    }
    final Loan loan = relatedRecords.getLoan();
    final LoanPolicy loanPolicy = relatedRecords.getLoanPolicy();
    final DateTime loanDate = relatedRecords.getLoan().getLoanDate();

    Optional<DateTime> optionalDueDateLimit = determineFixedSchedule(loanPolicy)
      .findDueDateFor(loan.getLoanDate());
    if (!optionalDueDateLimit.isPresent()) {
      return HttpResult.succeeded(relatedRecords);
    }
    DateTime dueDateLimit = optionalDueDateLimit.get();
    if (!PeriodUtil.isAfterDate(loan.getDueDate(), dueDateLimit)) {
      return HttpResult.succeeded(relatedRecords);
    }

    ClosedLibraryStrategy strategy =
      ClosedLibraryStrategyUtils.determineStrategyForMovingBackward(
        loanPolicy, loanDate, relatedRecords.getTimeZone());
    HttpResult<DateTime> calculatedDate =
      strategy.calculateDueDate(dueDateLimit, relatedRecords.getAdjustingOpeningDays());
    return calculatedDate.next(date -> {
      relatedRecords.getLoan().changeDueDate(date);
      return HttpResult.succeeded(relatedRecords);
    });
  }

  private FixedDueDateSchedules determineFixedSchedule(LoanPolicy loanPolicy) {
    if (isRenewal) {
      return loanPolicy.isFixed()
        ? loanPolicy.getRenewalFixedDueDateSchedules()
        : loanPolicy.getRenewalDueDateLimitSchedules();
    }
    return loanPolicy.getFixedDueDateSchedules();
  }
}
