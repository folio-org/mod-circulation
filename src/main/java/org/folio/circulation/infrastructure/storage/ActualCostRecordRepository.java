package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.storage.mappers.ActualCostRecordMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

public class ActualCostRecordRepository {
  private final CollectionResourceClient actualCostRecordStorageClient;

  public ActualCostRecordRepository(Clients clients) {
    actualCostRecordStorageClient = clients.actualCostRecordsStorage();
  }

  public CompletableFuture<Result<ActualCostRecord>> createActualCostRecord(
    ActualCostRecord actualCostRecord) {

    final ResponseInterpreter<ActualCostRecord> interpreter =
      new ResponseInterpreter<ActualCostRecord>()
        .flatMapOn(201, mapUsingJson(ActualCostRecordMapper::toDomain))
        .otherwise(forwardOnFailure());

    return actualCostRecordStorageClient.post(ActualCostRecordMapper.toJson(actualCostRecord))
      .thenApply(interpreter::flatMap);
  }

  public CompletableFuture<Result<ActualCostRecord>> getActualCostRecordByAccountId(
    String accountId) {

    if (accountId == null) {
      return ofAsync(() -> null);
    }

    return CqlQuery.exactMatch("accountId", accountId)
      .after(query -> actualCostRecordStorageClient.getMany(query, PageLimit.one()))
      .thenApply(r -> r.next(this::mapResponseToActualCostRecords))
      .thenApply(r -> r.map(multipleRecords -> multipleRecords.getRecords().stream()
        .findFirst()
        .orElse(null)));
  }

  private Result<MultipleRecords<ActualCostRecord>> mapResponseToActualCostRecords(Response response) {
    return MultipleRecords.from(response, ActualCostRecordMapper::toDomain, "actualCostRecords");
  }
}
