package org.folio.circulation.domain;


import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.Result;

public class AutomatedPatronBlocksRepository {
  private final CollectionResourceClient automatedPatronBlocksClient;

  public AutomatedPatronBlocksRepository(Clients clients) {
    automatedPatronBlocksClient = clients.automatedPatronBlocksClient();
  }

  public CompletableFuture<Result<AutomatedPatronBlocks>> findByUserId(String userId) {
    if(isNull(userId)) {
      return ofAsync(() -> null);
    }

    return FetchSingleRecord.<AutomatedPatronBlocks>forRecord("automatedPatronBlocks")
      .using(automatedPatronBlocksClient)
      .mapTo(AutomatedPatronBlocks::from)
      .fetch(userId)
      .thenCompose(r -> r.succeeded()
        ? completedFuture(succeeded(r.value()))
        : completedFuture(succeeded(new AutomatedPatronBlocks())));
  }
}
