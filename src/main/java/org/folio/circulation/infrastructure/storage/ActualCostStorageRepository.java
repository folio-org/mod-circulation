package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.storage.mappers.ActualCostRecordMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

public class ActualCostStorageRepository {
  private final CollectionResourceClient actualCostRecordStorageClient;

  private final ActualCostRecordMapper actualCostRecordMapper = new ActualCostRecordMapper();

  public ActualCostStorageRepository(Clients clients) {
    actualCostRecordStorageClient = clients.actualCostRecordStorageClient();
  }

  public CompletableFuture<Result<ActualCostRecord>> createActualCostRecord(
    ActualCostRecord actualCostRecord) {

    final ResponseInterpreter<ActualCostRecord> interpreter =
      new ResponseInterpreter<ActualCostRecord>()
        .flatMapOn(201, mapUsingJson(actualCostRecordMapper::toDomain))
        .otherwise(forwardOnFailure());

    return actualCostRecordStorageClient.post(actualCostRecordMapper.toJson(actualCostRecord))
      .thenApply(interpreter::flatMap);
  }
}
