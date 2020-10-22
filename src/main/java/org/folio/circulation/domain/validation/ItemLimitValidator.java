package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.resources.ItemLimitValidationStrategy;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class ItemLimitValidator {
  private final Clients clients;
  private final ItemLimitValidationStrategy validationStrategy;

  public ItemLimitValidator(ItemLimitValidationStrategy validationStrategy, Clients clients) {
    this.clients = clients;
    this.validationStrategy = validationStrategy;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> validate(LoanAndRelatedRecords records) {
    return validationStrategy.validate(records.getLoan(), clients)
      .thenApply(mapResult(ignored -> records));
  }

}
