package org.folio.circulation.resources;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public interface ItemLimitValidationStrategy {

  CompletableFuture<Result<Void>> validate(Loan loan, Clients clients);
}
