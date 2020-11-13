package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.DueDateStrategy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils;
import org.folio.circulation.domain.policy.library.LibraryTimetable;
import org.folio.circulation.domain.policy.library.LibraryTimetableConverter;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class LoanPolicyValidator {
  private final CalendarRepository calendarRepository;

  public LoanPolicyValidator(CalendarRepository calendarRepository) {
    this.calendarRepository = calendarRepository;
  }

  public Result<LoanAndRelatedRecords> refuseWhenLoanPolicyHasNoTimetable(
    Result<LoanAndRelatedRecords> result) {
    return result.combineToResult(getOpeningDays(result).join(), (l, a) -> {
      return result.failWhen(
        loanAndRelatedRecords -> succeeded(hasNoTimetable(loanAndRelatedRecords, a)),
        loanAndRelatedRecords -> ClosedLibraryStrategyUtils.failureForAbsentTimetable());
    });
  }

  private CompletableFuture<Result<AdjacentOpeningDays>> getOpeningDays(
    Result<LoanAndRelatedRecords> loanAndRelatedRecord) {
    return loanAndRelatedRecord.after(r -> getOpeningDays(r));
  }

  private CompletableFuture<Result<AdjacentOpeningDays>> getOpeningDays(
    LoanAndRelatedRecords loanAndRelatedRecord) {
    return calendarRepository.lookupOpeningDays(
      getDueDate(loanAndRelatedRecord).toLocalDate(),
      loanAndRelatedRecord.getLoan().getCheckoutServicePointId());
  }

  private DateTime getDueDate(LoanAndRelatedRecords loanAndRelatedRecord) {
    final DateTime systemTime = ClockManager.getClockManager().getDateTime();
    final Loan loan = loanAndRelatedRecord.getLoan();
    final RequestQueue requestQueue = loanAndRelatedRecord.getRequestQueue();
    final DueDateStrategy dueDateStrategy = loan.getLoanPolicy()
      .determineStrategy(requestQueue, false, false, systemTime);

    return dueDateStrategy.calculateDueDate(loan).value();
  }

  private boolean hasNoTimetable(LoanAndRelatedRecords loanAndRelatedRecord,
    AdjacentOpeningDays adjacentOpeningDays) {
    final DateTimeZone zone = loanAndRelatedRecord.getTimeZone();
    final DateTime dueDate = getDueDate(loanAndRelatedRecord);
    final LibraryTimetable libraryTimetable =
      LibraryTimetableConverter.convertToLibraryTimetable(adjacentOpeningDays, zone);

    return libraryTimetable.findInterval(dueDate) == null;
  }

}
