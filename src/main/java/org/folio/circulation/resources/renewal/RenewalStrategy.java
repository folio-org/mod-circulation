package org.folio.circulation.resources.renewal;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public interface RenewalStrategy {
  CompletableFuture<Result<RenewalContext>> renew(RenewalContext context, Clients clients);
}
