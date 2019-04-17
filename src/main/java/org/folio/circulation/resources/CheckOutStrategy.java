package org.folio.circulation.resources;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

/**
 * Represents the checkout process stage where
 * the appropriate status and due date are set for the loan.
 */
public interface CheckOutStrategy {

  CompletableFuture<Result<LoanAndRelatedRecords>> checkOut(LoanAndRelatedRecords relatedRecords,
                                                            JsonObject request,
                                                            Clients clients);
}
