package org.folio.circulation.infrastructure.storage;

import static java.util.function.Function.identity;
import static org.folio.circulation.storage.mappers.ActualCostRecordMapper.toJson;
import static org.folio.circulation.support.AsyncCoordinationUtil.mapSequentially;
import static org.folio.circulation.support.http.CommonResponseInterpreters.noContentRecordInterpreter;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.PageLimit.one;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.storage.mappers.ActualCostRecordMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.fetching.CqlQueryFinder;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;

public class ActualCostRecordRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient actualCostRecordStorageClient;

  private static final String ACTUAL_COST_RECORDS = "actualCostRecords";
  private static final String LOAN_ID_FIELD_NAME = "loan.id";

  public ActualCostRecordRepository(Clients clients) {
    actualCostRecordStorageClient = clients.actualCostRecordsStorage();
  }

  public CompletableFuture<Result<ActualCostRecord>> createActualCostRecord(
    ActualCostRecord actualCostRecord) {

    final ResponseInterpreter<ActualCostRecord> interpreter =
      new ResponseInterpreter<ActualCostRecord>()
        .flatMapOn(201, mapUsingJson(ActualCostRecordMapper::toDomain))
        .otherwise(forwardOnFailure());

    return actualCostRecordStorageClient.post(toJson(actualCostRecord))
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
    return MultipleRecords.from(response, ActualCostRecordMapper::toDomain, ACTUAL_COST_RECORDS);
  }

  public CompletableFuture<Result<Loan>> findByLoan(Result<Loan> loanResult) {
    return loanResult.after(loan -> createActualCostRecordCqlFinder().findByQuery(
        exactMatch(LOAN_ID_FIELD_NAME, loan.getId()), one())
      .thenApply(records -> records.map(MultipleRecords::firstOrNull))
      .thenApply(mapResult(ActualCostRecordMapper::toDomain))
      .thenApply(mapResult(loan::withActualCostRecord)));
  }

  public CompletableFuture<Result<Collection<ActualCostRecord>>> findExpiredActualCostRecords() {
    return CqlQuery.lessThan("expirationDate", ClockUtil.getZonedDateTime())
      .combine(CqlQuery.exactMatch("status", "Open"), CqlQuery::and)
      .after(cql -> actualCostRecordStorageClient.getMany(cql, PageLimit.oneThousand()))
      .thenApply(r -> r.next(this::mapResponseToActualCostRecords))
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  public CompletableFuture<Result<List<ActualCostRecord>>> update(
    Collection<ActualCostRecord> records) {

    return mapSequentially(records, this::update);
  }

  public CompletableFuture<Result<ActualCostRecord>> update(ActualCostRecord rec) {
    log.info("Updating actual cost record {}", rec.getId());
    return actualCostRecordStorageClient.put(rec.getId(), toJson(rec))
      .thenApply(noContentRecordInterpreter(rec)::flatMap)
      .whenComplete((r, t) -> log.info("Updated actual cost record {}", r.value().getId()));
  }

  private CqlQueryFinder<JsonObject> createActualCostRecordCqlFinder() {
    return new CqlQueryFinder<>(actualCostRecordStorageClient, ACTUAL_COST_RECORDS,
      identity());
  }

}
