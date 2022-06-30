package org.folio.circulation.infrastructure.storage;

import io.vertx.core.json.JsonObject;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.PageLimit.one;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.storage.mappers.ActualCostRecordMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.fetching.CqlQueryFinder;
import org.folio.circulation.support.fetching.GetManyRecordsRepository;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

public class ActualCostRecordRepository implements GetManyRecordsRepository<ActualCostRecord> {
  private final CollectionResourceClient actualCostRecordStorageClient;

  private static final String ACTUAL_COST_RECORDS_COLLECTION_PROPERTY_NAME = "actualCostRecords";
  private static final String LOAN_ID_FIELD_NAME = "loanId";

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

  public CompletableFuture<Result<Loan>> findByLoan(Result<Loan> loanResult) {
    return loanResult.after(loan -> createActualCostRecordFinder().findByQuery(
        exactMatch(LOAN_ID_FIELD_NAME, loan.getId()), one())
      .thenApply(records -> records.map(MultipleRecords::firstOrNull))
      .thenApply(mapResult(ActualCostRecordMapper::toDomain))
      .thenApply(mapResult(loan::withActualCostRecord)));
  }

  private CqlQueryFinder<JsonObject> createActualCostRecordFinder() {
    return new CqlQueryFinder<>(actualCostRecordStorageClient, ACTUAL_COST_RECORDS_COLLECTION_PROPERTY_NAME,
      identity());
  }

  public CompletableFuture<Result<MultipleRecords<ActualCostRecord>>> getMany(CqlQuery cqlQuery,
    PageLimit pageLimit, Offset offset) {
    return actualCostRecordStorageClient.getMany(cqlQuery, pageLimit, offset)
      .thenApply(flatMapResult(this::mapResponseToActualCostRecords));
  }

}
