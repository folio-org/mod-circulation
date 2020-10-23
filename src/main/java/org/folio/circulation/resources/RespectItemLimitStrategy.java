package org.folio.circulation.resources;

import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class RespectItemLimitStrategy extends ItemLimitHandlingStrategy {

  @Override
  public CompletableFuture<Result<Void>> handle(Loan loan, JsonObject request, Clients clients) {
    if (itemLimitIsNotSet(loan)) {
      return doNothing();
    }

    return succeeded(null).afterWhen(
      r -> isLimitReached(loan, clients),
      r -> fail(loan, true),
      r -> doNothing());
  }
}
