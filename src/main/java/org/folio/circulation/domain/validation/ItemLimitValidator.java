package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;

import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ItemLimitValidator {
  private final Function<String, ValidationErrorFailure> itemLimitErrorFunction;
  private final LoanRepository loanRepository;

  public ItemLimitValidator(Function<String, ValidationErrorFailure> itemLimitErrorFunction,
    LoanRepository loanRepository ) {
    this.itemLimitErrorFunction = itemLimitErrorFunction;
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemLimitIsReached(
    LoanAndRelatedRecords records) {
    Integer itemLimit = records.getLoan().getLoanPolicy().getItemLimit();
    if (itemLimit == null) {
      return completedFuture(succeeded(records));
    }

    return ofAsync(() -> itemLimit)
      .thenComposeAsync(result -> result.failAfter(limit -> isLimitReached(records, limit),
          limit -> itemLimitErrorFunction.apply(String.format("Patron has reached maximum limit of %d items", limit))))
      .thenApply(result -> result.map(v -> records));
  }

  private CompletableFuture<Result<Boolean>> isLimitReached(LoanAndRelatedRecords records, int itemLimit) {

    return loanRepository.findOpenLoansByUserId(records)
      .thenApply(r -> r.map(loans -> loans.getTotalRecords() >= itemLimit));
  }
}
