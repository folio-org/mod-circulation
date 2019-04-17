package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.CalendarRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.determineClosedLibraryStrategy;
import static org.folio.circulation.support.Result.succeeded;

public class ClosedLibraryStrategyService {

  public static ClosedLibraryStrategyService using(
    Clients clients, DateTime currentTime, boolean isRenewal) {
    return new ClosedLibraryStrategyService(new CalendarRepository(clients), currentTime, isRenewal);
  }

  private final CalendarRepository calendarRepository;
  private final DateTime currentDateTime;
  private final boolean isRenewal;

  public ClosedLibraryStrategyService(
    CalendarRepository calendarRepository, DateTime currentDateTime, boolean isRenewal) {
    this.calendarRepository = calendarRepository;
    this.currentDateTime = currentDateTime;
    this.isRenewal = isRenewal;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> applyClosedLibraryDueDateManagement(
    LoanAndRelatedRecords relatedRecords) {

    return applyClosedLibraryDueDateManagement(relatedRecords.getLoan(), relatedRecords.getLoanPolicy(), relatedRecords.getTimeZone())
      .thenApply(r -> r.map(dateTime -> {
        relatedRecords.getLoan().changeDueDate(dateTime);
        return relatedRecords;
      }));
  }

  public CompletableFuture<Result<DateTime>> applyClosedLibraryDueDateManagement(Loan loan, LoanPolicy loanPolicy, DateTimeZone timeZone) {
    LocalDate requestedDate = loan.getDueDate().withZone(timeZone).toLocalDate();
    return calendarRepository.lookupOpeningDays(requestedDate, loan.getCheckoutServicePointId())
      .thenApply(r -> r.next(openingDays -> applyStrategy(loan, loanPolicy, openingDays, timeZone)));
  }

  private Result<DateTime> applyStrategy(
    Loan loan, LoanPolicy loanPolicy, AdjacentOpeningDays openingDays, DateTimeZone timeZone) {
    DateTime initialDueDate = loan.getDueDate();
    ClosedLibraryStrategy strategy = determineClosedLibraryStrategy(loanPolicy, currentDateTime, timeZone);

    return strategy.calculateDueDate(initialDueDate, openingDays)
      .next(dateTime -> applyFixedDueDateLimit(dateTime, loan, loanPolicy, openingDays, timeZone));
  }

  private Result<DateTime> applyFixedDueDateLimit(
    DateTime dueDate, Loan loan, LoanPolicy loanPolicy, AdjacentOpeningDays openingDays, DateTimeZone timeZone) {
    Optional<DateTime> optionalDueDateLimit =
      loanPolicy.getScheduleLimit(loan.getLoanDate(), isRenewal, currentDateTime);
    if (!optionalDueDateLimit.isPresent()) {
      return succeeded(dueDate);
    }

    DateTime dueDateLimit = optionalDueDateLimit.get();
    Comparator<DateTime> dateComparator =
      Comparator.comparing(dateTime -> dateTime.withZone(timeZone).toLocalDate());
    if (dateComparator.compare(dueDate, dueDateLimit) <= 0) {
      return succeeded(dueDate);
    }

    ClosedLibraryStrategy strategy =
      ClosedLibraryStrategyUtils.determineStrategyForMovingBackward(
        loanPolicy, currentDateTime, timeZone);
    return strategy.calculateDueDate(dueDateLimit, openingDays);
  }
}
