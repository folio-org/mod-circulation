package org.folio.circulation.services.actualcostrecord;

import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.AsynchronousResult.fromFutureResult;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.services.CloseLoanWithLostItemService;
import org.folio.circulation.support.results.Result;

public class ActualCostRecordExpirationService {
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
    return fetchLoansByExpiredRecords()
      .thenCompose(r -> r.after(this::closeLoans));
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> fetchLoansByExpiredRecords() {
    return actualCostRecordRepository.findExpiredActualCostRecords()
      .thenCompose(r -> r.after(actualCostRecords -> loanRepository.findByIds(
        actualCostRecords.stream()
          .map(ActualCostRecord::getLoanId)
          .collect(toList()))));
  }

  private CompletableFuture<Result<Void>> closeLoans(MultipleRecords<Loan> expiredLoans) {
    if (expiredLoans.isEmpty()) {
      return ofAsync(() -> null);
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
}
