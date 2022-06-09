package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

public class ActualCostStorageRepository {
  private final CollectionResourceClient actualCostRecordStorageClient;

  public ActualCostStorageRepository(Clients clients) {
    actualCostRecordStorageClient = clients.actualCostRecordStorageClient();
  }

  public CompletableFuture<Result<Void>> createActualCostRecord(
    ActualCostRecord actualCostRecord) {

    final ResponseInterpreter<Void> interpreter =
      new ResponseInterpreter<Void>()
        .on(201, Result.succeeded(null))
        .otherwise(forwardOnFailure());

    return actualCostRecordStorageClient.post(actualCostRecord.toJson())
      .thenApply(interpreter::flatMap);
  }
}
