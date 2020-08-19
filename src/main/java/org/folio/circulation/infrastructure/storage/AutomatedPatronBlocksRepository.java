package org.folio.circulation.infrastructure.storage;


import static java.util.Objects.isNull;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.AutomatedPatronBlocks;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.results.Result;

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
      .whenNotFound(succeeded(new AutomatedPatronBlocks()))
      .fetch(userId);
  }
}
