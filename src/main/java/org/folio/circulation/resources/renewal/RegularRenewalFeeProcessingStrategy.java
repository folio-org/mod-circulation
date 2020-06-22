package org.folio.circulation.resources.renewal;

import static org.folio.circulation.domain.OverdueFineCalculatorService.using;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.OverdueFineCalculatorService;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

/**
 * Fee processing strategy that used to process fees/fines for regular renew.
 */
public class RegularRenewalFeeProcessingStrategy implements RenewalFeeProcessingStrategy {
  @Override
  public CompletableFuture<Result<RenewalContext>> processFeesFines(
    RenewalContext renewalContext, Clients clients) {

    final OverdueFineCalculatorService overdueFineService = using(clients);
    return overdueFineService.createOverdueFineIfNecessary(renewalContext);
  }
}
