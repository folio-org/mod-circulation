package org.folio.circulation.storage;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInOperation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.ResponseInterpreter;

public class CheckInOperationRepository {
  private final CollectionResourceClient checkInOperationStorageClient;

  public CheckInOperationRepository(Clients clients) {
    checkInOperationStorageClient = clients.checkInOperationStorageClient();
  }

  public CompletableFuture<Result<CheckInOperation>> logCheckInOperation(
    CheckInOperation checkInOperation) {

    final ResponseInterpreter<CheckInOperation> interpreter =
      new ResponseInterpreter<CheckInOperation>()
        .flatMapOn(201, mapUsingJson(CheckInOperation::from))
        .otherwise(forwardOnFailure());

    return checkInOperationStorageClient.post(checkInOperation.toJson())
      .thenApply(interpreter::flatMap);
  }
}
