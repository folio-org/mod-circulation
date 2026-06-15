package org.folio.circulation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.DeclareLostContext;
import org.folio.circulation.support.logging.RenewalPerformanceLogger;
import org.folio.circulation.support.results.Result;

public class StoreLoanAndItem {
  private static final Logger log = LogManager.getLogger(StoreLoanAndItem.class);
  private final LoanRepository loanRepository;
  private final ItemRepository itemRepository;

  public StoreLoanAndItem(LoanRepository loanRepository, ItemRepository itemRepository) {
    this.loanRepository = loanRepository;
    this.itemRepository = itemRepository;
  }

  public CompletableFuture<Result<RenewalContext>> updateLoanAndItemInStorage(
    RenewalContext relatedRecords) {

    RenewalContext updatedContext = RenewalPerformanceLogger.logAndAdvance(log,
      relatedRecords, System.currentTimeMillis(), "step={}", "store-loan-and-item-start");

    return updateLoanAndItemInStorage(updatedContext.getLoan())
      .thenApply(mapResult(updatedContext::withLoan))
      .thenApply(result -> logRenewalCompletion(result, updatedContext,
        "store-loan-and-item-complete"));
  }

  public CompletableFuture<Result<DeclareLostContext>> updateLoanAndItemInStorage(
    DeclareLostContext declareLostContext) {

    return updateLoanAndItemInStorage(declareLostContext.getLoan())
      .thenApply(mapResult(declareLostContext::withLoan));
  }

  public CompletableFuture<Result<Loan>> updateLoanAndItemInStorage(Loan loan) {
    if (loan == null || loan.getItem() == null) {
      return completedFuture(succeeded(null));
    }

    return updateItem(loan.getItem())
      .thenComposeAsync(response -> loanRepository.updateLoan(loan));
  }

  private CompletableFuture<Result<Item>> updateItem(Item item) {
    if (!item.hasChanged()) {
      return completedFuture(succeeded(item));
    }

    return itemRepository.updateItem(item);
  }

  private Result<RenewalContext> logRenewalCompletion(Result<RenewalContext> result,
    RenewalContext context, String stepName) {

    long currentMillis = System.currentTimeMillis();

    if (result.succeeded()) {
      return result.map(updatedContext -> RenewalPerformanceLogger.logAndAdvance(log,
        updatedContext, currentMillis, "step={}", stepName));
    }

    RenewalPerformanceLogger.log(log, context.getPerformanceAnalysisId(),
      context.getLastPerformanceTimestampMillis(), currentMillis,
      context.getLoan() != null ? context.getLoan().getId() : null,
      "step={} outcome={}", stepName, "failure");

    return result;
  }
}
