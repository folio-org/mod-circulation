package org.folio.circulation.infrastructure.storage;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInRecord;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.client.ResponseInterpreter;

import io.vertx.core.json.JsonObject;

public class CheckInStorageRepository {
  public static final String RECORDS_PROPERTY_NAME = "checkIns";

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

  public CompletableFuture<Result<JsonObject>> getById(String id) {
    return FetchSingleRecord.<JsonObject>forRecord(RECORDS_PROPERTY_NAME)
      .using(checkInStorageClient)
      .mapTo(identity())
      .whenNotFound(failed(new RecordNotFoundFailure(RECORDS_PROPERTY_NAME, id)))
      .fetch(id);
  }

  public CompletableFuture<Result<MultipleRecords<JsonObject>>> findBy(String query) {
    return checkInStorageClient.getManyWithRawQueryStringParameters(query)
      .thenApply(flatMapResult(response ->
        MultipleRecords.from(response, identity(), RECORDS_PROPERTY_NAME)));
  }
}
