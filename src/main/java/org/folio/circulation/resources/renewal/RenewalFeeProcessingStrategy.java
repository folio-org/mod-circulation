package org.folio.circulation.resources.renewal;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

public interface RenewalFeeProcessingStrategy {
  CompletableFuture<Result<RenewalContext>> processFeesFines(
    RenewalContext renewalContext, Clients clients);
}
