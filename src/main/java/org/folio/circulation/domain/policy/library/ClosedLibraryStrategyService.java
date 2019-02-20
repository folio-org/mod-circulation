package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.CalendarRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.FixedDueDateSchedules;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.determineClosedLibraryStrategy;

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

  public CompletableFuture<HttpResult<DateTime>> applyCLDDM(Loan loan, LoanPolicy loanPolicy, DateTimeZone timeZone) {
    return calendarRepository.lookupOpeningDays(loan)
      .thenApply(r -> r.next(openingDays -> applyStrategy(loan, loanPolicy, openingDays, timeZone)));
  }

  private HttpResult<DateTime> applyStrategy(
    Loan loan, LoanPolicy loanPolicy, AdjustingOpeningDays openingDays, DateTimeZone timeZone) {
    DateTime initialDueDate = loan.getDueDate();
    if (openingDays == null) {
      return HttpResult.succeeded(initialDueDate);
    }
    ClosedLibraryStrategy strategy = determineClosedLibraryStrategy(loanPolicy, currentTime, timeZone);

    return strategy.calculateDueDate(initialDueDate, openingDays)
      .next(dateTime -> applyFixedDueDateLimit(dateTime, loan, loanPolicy, openingDays, timeZone));
  }

  private HttpResult<DateTime> applyFixedDueDateLimit(
    DateTime dueDate, Loan loan, LoanPolicy loanPolicy, AdjustingOpeningDays openingDays, DateTimeZone timeZone) {
    Optional<DateTime> optionalDueDateLimit = determineFixedSchedule(loanPolicy)
      .findDueDateFor(loan.getLoanDate());
    if (!optionalDueDateLimit.isPresent()) {
      return HttpResult.succeeded(dueDate);
    }

    DateTime dueDateLimit = optionalDueDateLimit.get();
    Comparator<DateTime> dateComparator =
      Comparator.comparing(dateTime -> dateTime.withZone(DateTimeZone.UTC).toLocalDate());
    if (dateComparator.compare(dueDate, dueDateLimit) <= 0) {
      return HttpResult.succeeded(dueDate);
    }

    ClosedLibraryStrategy strategy =
      ClosedLibraryStrategyUtils.determineStrategyForMovingBackward(
        loanPolicy, currentTime, timeZone);
    return strategy.calculateDueDate(dueDateLimit, openingDays);
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
