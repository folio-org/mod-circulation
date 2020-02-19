package org.folio.circulation.storage;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInRecord;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.ResponseInterpreter;

public class CheckInStorageRepository {
  private final CollectionResourceClient checkInStorageClient;

  public CheckInStorageRepository(Clients clients) {
    checkInStorageClient = clients.checkInStorageClient();
  }

  public CompletableFuture<Result<Void>> createCheckInLogRecord(
    CheckInRecord checkInRecord) {

    final ResponseInterpreter<Void> interpreter =
      new ResponseInterpreter<Void>()
        .on(201, Result.succeeded(null))
        .otherwise(forwardOnFailure());

    return checkInStorageClient.post(checkInRecord.toJson())
      .thenApply(interpreter::flatMap);
  }
}
