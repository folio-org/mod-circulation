package org.folio.circulation.resources;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public interface RenewalStrategy {

  CompletableFuture<Result<LoanAndRelatedRecords>> renew(
    LoanAndRelatedRecords relatedRecords, JsonObject requestBody, Clients clients);
}
