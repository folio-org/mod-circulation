package org.folio.circulation.domain.policy.library;

import org.folio.circulation.domain.CalendarRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.PeriodUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ClosedLibraryStrategyService {

  public static ClosedLibraryStrategyService using(Clients clients, DateTime currentTime) {
    return new ClosedLibraryStrategyService(new CalendarRepository(clients), currentTime);
  }

  private final CalendarRepository calendarRepository;
  private final DateTime currentTime;

  public ClosedLibraryStrategyService(CalendarRepository calendarRepository, DateTime currentTime) {
    this.calendarRepository = calendarRepository;
    this.currentTime = currentTime;
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> applyCLDDM(LoanAndRelatedRecords relatedRecords) {
    return CompletableFuture.completedFuture(HttpResult.succeeded(relatedRecords))
      .thenComposeAsync(r -> r.after(calendarRepository::lookupPeriod))
      .thenApply(r -> r.next(this::applyStrategy))
      .thenComposeAsync(r -> r.after(calendarRepository::lookupPeriodForFixedDueDateSchedule))
      .thenApply(r -> r.next(this::applyFixedDueDateLimit));
  }

  private HttpResult<LoanAndRelatedRecords> applyStrategy(LoanAndRelatedRecords relatedRecords) {
    if (relatedRecords.getInitialDueDateDays() == null) {
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
      strategy.calculateDueDate(dueDate, relatedRecords.getInitialDueDateDays());
    return calculateDueDate.next(date -> {
      relatedRecords.getLoan().changeDueDate(date);
      return HttpResult.succeeded(relatedRecords);
    });
  }

  private HttpResult<LoanAndRelatedRecords> applyFixedDueDateLimit(LoanAndRelatedRecords relatedRecords) {
    if (relatedRecords.getFixedDueDateDays() == null) {
      return HttpResult.succeeded(relatedRecords);
    }
    final Loan loan = relatedRecords.getLoan();
    final LoanPolicy loanPolicy = relatedRecords.getLoanPolicy();
    final DateTime loanDate = relatedRecords.getLoan().getLoanDate();
    final DateTime dueDate = relatedRecords.getLoan().getDueDate();

    Optional<DateTime> optionalDueDateLimit = loanPolicy.getFixedDueDateSchedules()
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
      strategy.calculateDueDate(dueDate, relatedRecords.getFixedDueDateDays());
    return calculatedDate.next(date -> {
      relatedRecords.getLoan().changeDueDate(date);
      return HttpResult.succeeded(relatedRecords);
    });
  }
}
