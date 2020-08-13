package org.folio.circulation.resources.renewal;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.LostItemFeeRefundService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class OverrideRenewalFeeProcessingStrategy implements RenewalFeeProcessingStrategy {
  private final RegularRenewalFeeProcessingStrategy regularFeeProcessing =
    new RegularRenewalFeeProcessingStrategy();

  @Override
  public CompletableFuture<Result<RenewalContext>> processFeesFines(
    RenewalContext renewalContext, Clients clients) {

    final LostItemFeeRefundService lostFeeRefundService = new LostItemFeeRefundService(clients);

    return lostFeeRefundService.refundLostItemFees(renewalContext, servicePointId(renewalContext))
      .thenCompose(r -> r.after(context -> regularFeeProcessing.processFeesFines(context, clients)));
  }

  private String servicePointId(RenewalContext renewalContext) {
    return renewalContext.getRenewalRequest().getString("servicePointId");
  }
}
