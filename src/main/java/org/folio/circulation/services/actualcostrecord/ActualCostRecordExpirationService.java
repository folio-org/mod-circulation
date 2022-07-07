package org.folio.circulation.services.actualcostrecord;

import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_STATUS;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.AsynchronousResult.fromFutureResult;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.services.CloseLoanWithLostItemService;
import org.folio.circulation.support.fetching.PageableFetcher;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

public class ActualCostRecordExpirationService {
  private final PageableFetcher<Loan> loanPageableFetcher;
  private final CloseLoanWithLostItemService closeLoanWithLostItemService;
  private final ItemRepository itemRepository;
  private final AccountRepository accountRepository;
  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final ActualCostRecordRepository actualCostRecordRepository;

  public ActualCostRecordExpirationService(PageableFetcher<Loan> loanPageableFetcher,
    CloseLoanWithLostItemService closeLoanWithLostItemService, ItemRepository itemRepository,
    AccountRepository accountRepository, LostItemPolicyRepository lostItemPolicyRepository,
    ActualCostRecordRepository actualCostRecordRepository) {

    this.itemRepository = itemRepository;
    this.loanPageableFetcher = loanPageableFetcher;
    this.closeLoanWithLostItemService = closeLoanWithLostItemService;
    this.accountRepository = accountRepository;
    this.lostItemPolicyRepository = lostItemPolicyRepository;
    this.actualCostRecordRepository = actualCostRecordRepository;
  }

  public CompletableFuture<Result<Void>> expireActualCostRecords() {
    return fetchLoansForLostItemsQuery()
      .after(query -> loanPageableFetcher.processPages(query, this::closeLoans));
  }

  private Result<CqlQuery> fetchLoansForLostItemsQuery() {
    return CqlQuery.exactMatch(ITEM_STATUS, DECLARED_LOST.getValue());
  }

  private CompletableFuture<Result<Void>> closeLoans(MultipleRecords<Loan> expiredLoans) {
    if (expiredLoans.isEmpty()) {
      return ofAsync(() -> null);
    }

    return fromFutureResult(itemRepository.fetchItems(succeeded(expiredLoans), Loan::withItem)
      .thenApply(r -> r.next(this::excludeLoansWithNonexistentItems)))
      .flatMapFuture(accountRepository::findAccountsForLoans)
      .flatMapFuture(lostItemPolicyRepository::findLostItemPoliciesForLoans)
      .flatMapFuture(actualCostRecordRepository::fetchActualCostRecords)
      .flatMapFuture(loans -> allOf(loans.getRecords(),
        closeLoanWithLostItemService::tryCloseLoanForActualCostExpiration))
      .toCompletableFuture()
      .thenApply(r -> r.map(ignored -> null));
  }

  private Result<MultipleRecords<Loan>> excludeLoansWithNonexistentItems(
    MultipleRecords<Loan> loans) {

    return succeeded(loans.filter(loan -> loan.getItem().isFound()));
  }
}
