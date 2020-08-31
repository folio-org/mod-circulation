package org.folio.circulation.resources.renewal;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.OverdueFineCalculatorService;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class RegularRenewalFeeProcessingStrategy implements RenewalFeeProcessingStrategy {
  @Override
  public CompletableFuture<Result<RenewalContext>> processFeesFines(
    RenewalContext renewalContext, Clients clients) {

    final OverdueFineCalculatorService overdueFineService = new OverdueFineCalculatorService(clients);
    return overdueFineService.createOverdueFineIfNecessary(renewalContext);
  }
}
