package org.folio.circulation.infrastructure.storage;

import static java.util.function.Function.identity;
import static org.folio.circulation.domain.ActualCostRecord.Status.OPEN;
import static org.folio.circulation.storage.mappers.ActualCostRecordMapper.toJson;
import static org.folio.circulation.support.AsyncCoordinationUtil.mapSequentially;
import static org.folio.circulation.support.CqlSortBy.sortBy;
import static org.folio.circulation.support.CqlSortClause.descending;
import static org.folio.circulation.support.http.CommonResponseInterpreters.noContentRecordInterpreter;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.PageLimit.one;
import static org.folio.circulation.support.http.client.PageLimit.oneThousand;
import static org.folio.circulation.support.logging.LogHelper.asString;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
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
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;

public class ActualCostRecordRepository {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient actualCostRecordStorageClient;

  private static final String ACTUAL_COST_RECORDS = "actualCostRecords";
  private static final String LOAN_ID_FIELD_NAME = "loan.id";
  private static final String STATUS_FIELD_NAME = "status";
  private static final String LOSS_DATE_FIELD_NAME = "lossDate";

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

    return find(exactMatch("feeFine.accountId", accountId), one())
      .thenApply(r -> r.map(MultipleRecords::firstOrNull));
  }

  public CompletableFuture<Result<ActualCostRecord>> findMostRecentOpenRecordForLoan(Loan loan) {
    log.debug("findMostRecentOpenRecordForLoan:: loanId={}", loan.getId());

    Result<CqlQuery> query = exactMatch(LOAN_ID_FIELD_NAME, loan.getId())
      .combine(exactMatch(STATUS_FIELD_NAME, OPEN.getValue()), CqlQuery::and)
      .map(q -> q.sortBy(sortBy(descending(LOSS_DATE_FIELD_NAME))));

    return findOne(query);
  }

  public CompletableFuture<Result<Loan>> findByLoan(Loan loan) {
    Result<CqlQuery> query = exactMatch(LOAN_ID_FIELD_NAME, loan.getId())
      .map(q -> q.sortBy(sortBy(descending(LOSS_DATE_FIELD_NAME))));

    return findOne(query)
      .thenApply(mapResult(loan::withActualCostRecord));
  }

  public CompletableFuture<Result<Collection<ActualCostRecord>>> findExpiredActualCostRecords() {
    Result<CqlQuery> query = CqlQuery.lessThan("expirationDate", ClockUtil.getZonedDateTime())
      .combine(exactMatch(STATUS_FIELD_NAME, "Open"), CqlQuery::and);

    return find(query, oneThousand())
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  private CompletableFuture<Result<ActualCostRecord>> findOne(Result<CqlQuery> query) {
    return find(query, one())
      .thenApply(records -> records.map(MultipleRecords::firstOrNull));
  }

  private CompletableFuture<Result<MultipleRecords<ActualCostRecord>>> find(Result<CqlQuery> query,
    PageLimit pageLimit) {

    return createActualCostRecordCqlFinder()
      .findByQuery(query, pageLimit)
      .thenApply(r -> r.map(records -> records.mapRecords(ActualCostRecordMapper::toDomain)))
      .thenApply(r -> r.peek(rec -> log.info("find:: found {}", asString(rec, ActualCostRecord::getId))));
  }

  public CompletableFuture<Result<Collection<ActualCostRecord>>> update(
    Collection<ActualCostRecord> records) {

    return mapSequentially(records, this::update);
  }

  private CompletableFuture<Result<ActualCostRecord>> update(ActualCostRecord actualCostRecord) {
    return actualCostRecordStorageClient.put(actualCostRecord.getId(), toJson(actualCostRecord))
      .thenApply(noContentRecordInterpreter(actualCostRecord)::flatMap);
  }

  private CqlQueryFinder<JsonObject> createActualCostRecordCqlFinder() {
    return new CqlQueryFinder<>(actualCostRecordStorageClient, ACTUAL_COST_RECORDS,
      identity());
  }

}
