package org.folio.circulation.storage;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInLogRecord;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.ResponseInterpreter;

public class CheckInStorageRepository {
  private final CollectionResourceClient checkInStorageClient;

  public CheckInStorageRepository(Clients clients) {
    checkInStorageClient = clients.checkInStorageClient();
  }

  public CompletableFuture<Result<CheckInLogRecord>> createCheckInLogRecord(
    CheckInLogRecord checkInLogRecord) {

    final ResponseInterpreter<CheckInLogRecord> interpreter =
      new ResponseInterpreter<CheckInLogRecord>()
        .flatMapOn(201, mapUsingJson(CheckInLogRecord::from))
        .otherwise(forwardOnFailure());

    return checkInStorageClient.post(checkInLogRecord.toJson())
      .thenApply(interpreter::flatMap);
  }
}
