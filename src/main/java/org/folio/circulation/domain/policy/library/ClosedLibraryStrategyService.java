package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.determineClosedLibraryStrategy;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.determineClosedLibraryStrategyForTruncatedDueDate;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class ClosedLibraryStrategyService {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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

    log.debug("applyClosedLibraryDueDateManagement:: parameters relatedRecords: {}",
      relatedRecords.getLoan());

    return applyClosedLibraryDueDateManagement(relatedRecords, false);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> applyClosedLibraryDueDateManagement(
    LoanAndRelatedRecords relatedRecords, boolean isRecall) {

    log.debug("applyClosedLibraryDueDateManagement:: parameters relatedRecords: {}, isRecall: {}",
      relatedRecords.getLoan(), isRecall);
    final Loan loan = relatedRecords.getLoan();

    return applyClosedLibraryDueDateManagement(loan, loan.getLoanPolicy(),
      relatedRecords.getTimeZone(), isRecall)
      .thenApply(mapResult(loan::changeDueDate))
      .thenApply(mapResult(relatedRecords::withLoan));
  }

  public CompletableFuture<Result<RenewalContext>> applyClosedLibraryDueDateManagement(
    RenewalContext renewalContext) {

    final Loan loan = renewalContext.getLoan();
    log.debug("applyClosedLibraryDueDateManagement:: parameters renewalContext: {}",
      renewalContext.getLoan());

    return applyClosedLibraryDueDateManagement(loan, loan.getLoanPolicy(),
      renewalContext.getTimeZone())
      .thenApply(mapResult(loan::changeDueDate))
      .thenApply(r -> r.next(updatedLoan -> {
        log.info("applyClosedLibraryDueDateManagement:: loan after applying closed " +
            "library due date management: {}", updatedLoan);
        return succeeded(updatedLoan);
      }))
      .thenApply(mapResult(renewalContext::withLoan));
  }

  private CompletableFuture<Result<ZonedDateTime>> applyClosedLibraryDueDateManagement(
    Loan loan, LoanPolicy loanPolicy, ZoneId timeZone) {
    return applyClosedLibraryDueDateManagement(loan, loanPolicy, timeZone, false);
  }

  private CompletableFuture<Result<ZonedDateTime>> applyClosedLibraryDueDateManagement(
    Loan loan, LoanPolicy loanPolicy, ZoneId timeZone, boolean isRecall) {

    log.debug("applyClosedLibraryDueDateManagement:: parameters loan: {}," +
        "loanPolicy: {}, timeZone: {}, isRecall: {}", loan, loanPolicy, timeZone, isRecall);
    LocalDate requestedDate = loan.getDueDate().withZoneSameInstant(timeZone).toLocalDate();

    return calendarRepository.lookupOpeningDays(requestedDate, loan.getCheckoutServicePointId())
      .thenApply(r -> r.next(openingDays -> applyStrategy(loan, loanPolicy, openingDays, timeZone, isRecall)))
      .thenCompose(r -> r.after(dueDate -> truncateDueDateIfPatronExpiresEarlier(dueDate, loan,
        loanPolicy, timeZone)));
  }

  private Result<ZonedDateTime> applyStrategy(
    Loan loan, LoanPolicy loanPolicy, AdjacentOpeningDays openingDays, ZoneId timeZone,
    boolean isRecall) {

    log.debug("applyStrategy:: parameters loan: {}, loanPolicy: {}, openingDays: {}, " +
        "timeZone: {}, isRecall: {}", loan, loanPolicy, openingDays, timeZone, isRecall);

    return determineClosedLibraryStrategy(loanPolicy, currentDateTime, timeZone)
      .calculateDueDate(loan.getDueDate(), openingDays)
      .next(dateTime -> applyFixedDueDateLimit(dateTime, loan, loanPolicy, openingDays, timeZone, isRecall))
      .next(dateTime -> {
        log.info("applyStrategy:: result: {}", dateTime);
        return succeeded(dateTime);
      });
  }

  private CompletableFuture<Result<ZonedDateTime>> truncateDueDateIfPatronExpiresEarlier(
    ZonedDateTime dueDate, Loan loan, LoanPolicy loanPolicy, ZoneId timeZone) {

    log.debug("truncateDueDateIfPatronExpiresEarlier:: parameters dueDate: {}, loan: {}, " +
        "loanPolicy: {}, timeZone: {}", dueDate, loan, loanPolicy, timeZone);
    User user = loan.getUser();
    if (user != null && user.getExpirationDate() != null &&
      isBeforeMillis(user.getExpirationDate(), dueDate)) {

      log.info("truncateDueDateIfPatronExpiresEarlier:: truncating dueDate");
      loan.setDueDateChangedByNearExpireUser();

      return calendarRepository.lookupOpeningDays(user.getExpirationDate().toLocalDate(),
        loan.getCheckoutServicePointId())
        .thenApply(r -> r.next(openingDays -> calculateTruncatedDueDate(user.getExpirationDate(),
          loanPolicy, timeZone, openingDays)))
        .thenApply(r -> r.next(dateTime -> {
          log.info("truncateDueDateIfPatronExpiresEarlier:: result: {}", dateTime);
          return succeeded(dateTime);
        }));
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
    ZoneId timeZone, boolean isRecall) {

    log.debug("applyFixedDueDateLimit:: parameters dueDate: {}, loan: {}, " +
      "loanPolicy: {}, openingDays: {}, timeZone: {}, isRecall: {}", dueDate,
      loan, loanPolicy, openingDays, timeZone, isRecall);

    Optional<ZonedDateTime> optionalDueDateLimit =
      loanPolicy.getScheduleLimit(isRecall ? loan.getDueDate() : loan.getLoanDate(), isRenewal,
        currentDateTime);
    if (!optionalDueDateLimit.isPresent()) {
      log.info("applyFixedDueDateLimit:: optionalDueDateLimit is not present");
      return succeeded(dueDate);
    }

    ZonedDateTime dueDateLimit = optionalDueDateLimit.get();
    Comparator<ZonedDateTime> dateComparator =
      Comparator.comparing(dateTime -> dateTime.withZoneSameInstant(timeZone).toLocalDate());
    if (dateComparator.compare(dueDate, dueDateLimit) <= 0) {
      log.info("applyFixedDueDateLimit:: dueDateLimit should not be applied");
      return succeeded(dueDate);
    }

    ClosedLibraryStrategy strategy = ClosedLibraryStrategyUtils.determineStrategyForMovingBackward(
        loanPolicy, currentDateTime, timeZone);

    return strategy.calculateDueDate(dueDateLimit, openingDays);
  }
}
