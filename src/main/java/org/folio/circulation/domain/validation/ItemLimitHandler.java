package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.resources.ItemLimitHandlingStrategy;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class ItemLimitHandler {
  private final ItemLimitHandlingStrategy strategy;
  private final JsonObject request;
  private final Clients clients;

  public ItemLimitHandler(ItemLimitHandlingStrategy strategy, JsonObject request, Clients clients) {
    this.strategy = strategy;
    this.request = request;
    this.clients = clients;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> handle(LoanAndRelatedRecords records) {
    return strategy.handle(records.getLoan(), request, clients)
      .thenApply(mapResult(ignored -> records));
  }

}
