package org.folio.circulation.resources;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.support.Result;

import io.vertx.core.http.HttpClient;

public class OverrideCheckOutByBarcodeResource extends CheckOutByBarcodeResource {

  public OverrideCheckOutByBarcodeResource(HttpClient client) {
    super(client, "/circulation/override-check-out-by-barcode");
  }

  @Override
  CompletableFuture<Result<LoanAndRelatedRecords>> applyLoanPolicy(LoanAndRelatedRecords relatedRecords,
                                                                   ClosedLibraryStrategyService strategyService) {
    return null;
  }
}
