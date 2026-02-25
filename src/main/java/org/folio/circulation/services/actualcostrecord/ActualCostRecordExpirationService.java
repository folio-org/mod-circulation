package org.folio.circulation.services.actualcostrecord;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.domain.ActualCostRecord.Status.EXPIRED;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.AsynchronousResult.fromFutureResult;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordLoan;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.services.CloseLoanWithLostItemService;
import org.folio.circulation.support.results.Result;

public class ActualCostRecordExpirationService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CloseLoanWithLostItemService closeLoanWithLostItemService;
  private final ItemRepository itemRepository;
  private final ActualCostRecordRepository actualCostRecordRepository;
  private final LoanRepository loanRepository;

  public ActualCostRecordExpirationService(
    CloseLoanWithLostItemService closeLoanWithLostItemService, ItemRepository itemRepository,
    ActualCostRecordRepository actualCostRecordRepository, LoanRepository loanRepository) {
    this.itemRepository = itemRepository;
    this.closeLoanWithLostItemService = closeLoanWithLostItemService;
    this.actualCostRecordRepository = actualCostRecordRepository;
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<Void>> expireActualCostRecords() {
    log.debug("expireActualCostRecords:: starting expired actual cost records expiration process");
    return actualCostRecordRepository.findExpiredActualCostRecords()
      .thenCompose(r -> r.after(this::processExpiredActualCostRecords))
      .whenComplete(this::logResult);
  }

  private CompletableFuture<Result<Void>> processExpiredActualCostRecords(
    Collection<ActualCostRecord> records) {

    log.debug("processExpiredActualCostRecords:: parameters records: {}",
      () -> collectionAsString(records));
    if (records.isEmpty()) {
      log.info("processExpiredActualCostRecords:: found no expired actual cost records to process");
      return emptyAsync();
    }

    log.info("Processing {} expired actual cost records", records.size());

    List<ActualCostRecord> expiredRecords = records.stream()
      .map(rec -> rec.withStatus(EXPIRED))
      .collect(toList());
    log.debug("processExpiredActualCostRecords:: marked {} records with EXPIRED status", expiredRecords.size());
    return actualCostRecordRepository.update(expiredRecords)
      .thenCompose(r -> r.after(this::fetchLoansForExpiredRecords))
      .thenCompose(r -> r.after(this::closeLoans));
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> fetchLoansForExpiredRecords(
    Collection<ActualCostRecord> records) {

    return loanRepository.findByIds(
      records.stream()
        .map(ActualCostRecord::getLoan)
        .map(ActualCostRecordLoan::getId)
        .collect(toSet()));
  }

  private CompletableFuture<Result<Void>> closeLoans(MultipleRecords<Loan> expiredLoans) {
    log.debug("closeLoans:: parameters expiredLoans: {}", () -> multipleRecordsAsString(expiredLoans));
    if (expiredLoans.isEmpty()) {
      log.info("closeLoans:: no loans to close, returning empty result");
      return emptyAsync();
    }

    return fromFutureResult(itemRepository.fetchItems(succeeded(expiredLoans))
      .thenApply(r -> r.next(this::excludeLoansWithNonexistentItems)))
      .flatMapFuture(loans -> allOf(loans.getRecords(),
        closeLoanWithLostItemService::closeLoanAsLostAndPaid))
      .toCompletableFuture()
      .thenApply(r -> r.map(ignored -> null));
  }

  private Result<MultipleRecords<Loan>> excludeLoansWithNonexistentItems(
    MultipleRecords<Loan> loans) {

    return succeeded(loans.filter(loan -> loan.getItem().isFound()));
  }

  private void logResult(Result<?> result, Throwable throwable) {
    if (throwable != null) {
      log.error("expireActualCostRecords:: an exception was thrown while processing expired actual cost records",
        throwable);
    } else if (result != null) {
      if (result.failed()) {
        log.error("expireActualCostRecords:: processing expired actual cost records failed: {}",
          result.cause());
      } else {
        log.info("expireActualCostRecords:: successfully finished processing expired actual cost records");
      }
    }
  }
}
