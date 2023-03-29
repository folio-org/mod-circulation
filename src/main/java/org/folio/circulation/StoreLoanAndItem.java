package org.folio.circulation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.DeclareLostContext;
import org.folio.circulation.support.results.Result;

public class StoreLoanAndItem {
  private final LoanRepository loanRepository;
  private final ItemRepository itemRepository;

  public StoreLoanAndItem(LoanRepository loanRepository, ItemRepository itemRepository) {
    this.loanRepository = loanRepository;
    this.itemRepository = itemRepository;
  }

  public CompletableFuture<Result<RenewalContext>> updateLoanAndItemInStorage(
    RenewalContext relatedRecords) {

    return updateLoanAndItemInStorage(relatedRecords.getLoan())
      .thenApply(mapResult(relatedRecords::withLoan));
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
}
