package org.folio.circulation.infrastructure.storage.inventory;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Request;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

public class InstanceRepository {
  private final CollectionResourceClient instancesClient;

  public InstanceRepository(Clients clients) {
    instancesClient = clients.instancesStorage();
  }

  public CompletableFuture<Result<Instance>> fetch(Request request) {
    return fetchById(request.getInstanceId());
  }

  private CompletableFuture<Result<Instance>> fetchById(String instanceId) {
    return SingleRecordFetcher.jsonOrNull(instancesClient, "instance")
      .fetch(instanceId)
      .thenApply(r -> r.map(Instance::from));
  }
}
