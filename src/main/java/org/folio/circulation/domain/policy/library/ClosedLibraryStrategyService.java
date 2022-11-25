package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.*;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.ExpirationDateManagement;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class ClosedLibraryStrategyService {

  public static ClosedLibraryStrategyService using(
    Clients clients, ZonedDateTime currentTime, boolean isRenewal) {
    return new ClosedLibraryStrategyService(new CalendarRepository(clients), currentTime, isRenewal);
  }

  private final CalendarRepository calendarRepository;
  private final ZonedDateTime currentDateTime;
  private final boolean isRenewal;

  public ClosedLibraryStrategyService(
    CalendarRepository calendarRepository, ZonedDateTime currentDateTime, boolean isRenewal) {
    this.calendarRepository = calendarRepository;
    this.currentDateTime = currentDateTime;
    this.isRenewal = isRenewal;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> applyClosedLibraryDueDateManagement(
    LoanAndRelatedRecords relatedRecords) {
    return applyClosedLibraryDueDateManagement(relatedRecords, false);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> applyClosedLibraryDueDateManagement(
    LoanAndRelatedRecords relatedRecords, boolean isRecall) {

    final Loan loan = relatedRecords.getLoan();

    return applyClosedLibraryDueDateManagement(loan, loan.getLoanPolicy(),
      relatedRecords.getTimeZone(), isRecall)
      .thenApply(mapResult(loan::changeDueDate))
      .thenApply(mapResult(relatedRecords::withLoan));
  }

  public CompletableFuture<Result<RenewalContext>> applyClosedLibraryDueDateManagement(
    RenewalContext renewalContext) {

    final Loan loan = renewalContext.getLoan();
    return applyClosedLibraryDueDateManagement(loan, loan.getLoanPolicy(),
      renewalContext.getTimeZone())
      .thenApply(mapResult(loan::changeDueDate))
      .thenApply(mapResult(renewalContext::withLoan));
  }

  private CompletableFuture<Result<ZonedDateTime>> applyClosedLibraryDueDateManagement(
    Loan loan, LoanPolicy loanPolicy, ZoneId timeZone) {
    return applyClosedLibraryDueDateManagement(loan, loanPolicy, timeZone, false);
  }

  private CompletableFuture<Result<ZonedDateTime>> applyClosedLibraryDueDateManagement(
    Loan loan, LoanPolicy loanPolicy, ZoneId timeZone, boolean isRecall) {

    LocalDate requestedDate = loan.getDueDate().withZoneSameInstant(timeZone).toLocalDate();

    return calendarRepository.lookupOpeningDays(requestedDate, loan.getCheckoutServicePointId())
      .thenApply(r -> r.next(openingDays -> applyStrategy(loan, loanPolicy, openingDays, timeZone, isRecall)))
      .thenCompose(r -> r.after(dueDate -> truncateDueDateIfPatronExpiresEarlier(dueDate, loan,
        loanPolicy, timeZone)));
  }

  private Result<ZonedDateTime> applyStrategy(
    Loan loan, LoanPolicy loanPolicy, AdjacentOpeningDays openingDays, ZoneId timeZone,
    boolean isRecall) {

    return determineClosedLibraryStrategy(loanPolicy, currentDateTime, timeZone)
      .calculateDueDate(loan.getDueDate(), openingDays)
      .next(dateTime -> applyFixedDueDateLimit(dateTime, loan, loanPolicy, openingDays, timeZone, isRecall));
  }

  private CompletableFuture<Result<ZonedDateTime>> truncateDueDateIfPatronExpiresEarlier(
    ZonedDateTime dueDate, Loan loan, LoanPolicy loanPolicy, ZoneId timeZone) {

    User user = loan.getUser();
    if (user != null && user.getExpirationDate() != null &&
      isBeforeMillis(user.getExpirationDate(), dueDate)) {

      return calendarRepository.lookupOpeningDays(user.getExpirationDate().toLocalDate(),
        loan.getCheckoutServicePointId())
        .thenApply(r -> r.next(openingDays -> calculateTruncatedDueDate(user.getExpirationDate(),
          loanPolicy, timeZone, openingDays)));
    }

    return ofAsync(() -> dueDate);
  }

  private Result<ZonedDateTime> calculateTruncatedDueDate(ZonedDateTime patronExpirationDate,
    LoanPolicy loanPolicy, ZoneId timeZone, AdjacentOpeningDays openingDays) {

      return determineClosedLibraryStrategyForTruncatedDueDate(loanPolicy, patronExpirationDate, timeZone)
        .calculateDueDate(patronExpirationDate, openingDays);
  }

  private Result<ZonedDateTime> applyFixedDueDateLimit(
    ZonedDateTime dueDate, Loan loan, LoanPolicy loanPolicy, AdjacentOpeningDays openingDays,
    ZoneId timeZone, boolean isRecall){

    Optional<ZonedDateTime> optionalDueDateLimit =
      loanPolicy.getScheduleLimit(isRecall ? loan.getDueDate() : loan.getLoanDate(), isRenewal,
        currentDateTime);
    if (!optionalDueDateLimit.isPresent()) {
      return succeeded(dueDate);
    }

    ZonedDateTime dueDateLimit = optionalDueDateLimit.get();
    Comparator<ZonedDateTime> dateComparator =
      Comparator.comparing(dateTime -> dateTime.withZoneSameInstant(timeZone).toLocalDate());
    if (dateComparator.compare(dueDate, dueDateLimit) <= 0) {
      return succeeded(dueDate);
    }

    ClosedLibraryStrategy strategy =
      ClosedLibraryStrategyUtils.determineStrategyForMovingBackward(
        loanPolicy, currentDateTime, timeZone);
    return strategy.calculateDueDate(dueDateLimit, openingDays);
  }

  public Result<ZonedDateTime> applyClosedLibraryStrategyForHoldShelfExpirationDate(
    ExpirationDateManagement expirationDateManagement, ZonedDateTime holdShelfExpirationDate,
    ZoneId tenantTimeZone, AdjacentOpeningDays openingDays) {

    return determineClosedLibraryStrategyForHoldShelfExpirationDate(
      expirationDateManagement, holdShelfExpirationDate, tenantTimeZone)
      .calculateDueDate(holdShelfExpirationDate, openingDays);
  }
}
