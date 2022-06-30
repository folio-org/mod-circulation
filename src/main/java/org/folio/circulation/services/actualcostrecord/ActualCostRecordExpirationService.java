package org.folio.circulation.services.actualcostrecord;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.services.CloseLoanWithLostItemService;
import org.folio.circulation.support.fetching.PageableFetcher;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_STATUS;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.AsynchronousResult.fromFutureResult;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

public class ActualCostRecordExpirationService {
  private final PageableFetcher<Loan> loanPageableFetcher;
  private final CloseLoanWithLostItemService closeLoanWithLostItemService;
  private final ItemRepository itemRepository;

  public ActualCostRecordExpirationService(PageableFetcher<Loan> loanPageableFetcher,
    CloseLoanWithLostItemService closeLoanWithLostItemService, ItemRepository itemRepository) {
    this.itemRepository = itemRepository;
    this.loanPageableFetcher = loanPageableFetcher;
    this.closeLoanWithLostItemService = closeLoanWithLostItemService;
  }

  public CompletableFuture<Result<Void>> expireActualCostRecords() {
    return loanFetchQuery()
      .after(query -> loanPageableFetcher.processPages(query, this::closeLoans));
  }

  private Result<CqlQuery> loanFetchQuery() {
    return CqlQuery.exactMatch(ITEM_STATUS, DECLARED_LOST.getValue());
  }

  private CompletableFuture<Result<Void>> closeLoans(MultipleRecords<Loan> expiredLoans) {
    if (expiredLoans.isEmpty()) {
      return ofAsync(() -> null);
    }

    return fromFutureResult(itemRepository.fetchItemsFor(succeeded(expiredLoans), Loan::withItem)
      .thenApply(r -> r.next(this::excludeLoansWithNonexistentItems)))
      .flatMapFuture(loans -> allOf(loans.getRecords(), closeLoanWithLostItemService::tryCloseLoan))
      .toCompletableFuture()
      .thenApply(r -> r.map(ignored -> null));
  }

  private Result<MultipleRecords<Loan>> excludeLoansWithNonexistentItems(
    MultipleRecords<Loan> loans) {

    return succeeded(loans.filter(loan -> loan.getItem().isFound()));
  }
}
