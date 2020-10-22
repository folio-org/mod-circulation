package org.folio.circulation.resources;

import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class OverrideItemLimitValidationStrategy implements ItemLimitValidationStrategy {

  @Override
  public CompletableFuture<Result<Void>> validate(Loan loan, Clients clients) {
    return ofAsync(() -> null);
  }
}
