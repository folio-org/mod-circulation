package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.schedule.JsonScheduledNoticeMapper.mapToJson;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.http.CommonResponseInterpreters.noContentRecordInterpreter;
import static org.folio.circulation.support.http.ResponseMapping.flatMapUsingJson;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class ScheduledNoticesRepository {
  public static ScheduledNoticesRepository using(Clients clients) {
    return new ScheduledNoticesRepository(
      clients.scheduledNoticesStorageClient());
  }

  private final CollectionResourceClient scheduledNoticesStorageClient;

  private ScheduledNoticesRepository(
    CollectionResourceClient scheduledNoticesStorageClient) {
    this.scheduledNoticesStorageClient = scheduledNoticesStorageClient;
  }

  public CompletableFuture<Result<ScheduledNotice>> create(ScheduledNotice scheduledNotice) {
    JsonObject representation = mapToJson(scheduledNotice);

    final ResponseInterpreter<ScheduledNotice> interpreter
      = new ResponseInterpreter<ScheduledNotice>()
      .flatMapOn(201, flatMapUsingJson(JsonScheduledNoticeMapper::mapFromJson));

    return scheduledNoticesStorageClient.post(representation)
      .thenApply(interpreter
        ::apply);
  }

  public CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    DateTime systemTime, int pageLimit) {
    return CqlQuery.lessThan("nextRunTime", systemTime.withZone(DateTimeZone.UTC))
      .map(cqlQuery -> cqlQuery.sortBy(ascending("nextRunTime")))
      .after(query -> findBy(query, pageLimit));
  }

  private CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findBy(
    CqlQuery cqlQuery, int pageLimit) {
    return scheduledNoticesStorageClient.getMany(cqlQuery, pageLimit)
      .thenApply(r -> r.next(response ->
        MultipleRecords.from(response, Function.identity(), "scheduledNotices")))
      .thenApply(r -> r.next(records -> records.flatMapRecords(JsonScheduledNoticeMapper::mapFromJson)));
  }

  public CompletableFuture<Result<ScheduledNotice>> update(ScheduledNotice scheduledNotice) {
    return scheduledNoticesStorageClient.put(scheduledNotice.getId(), mapToJson(scheduledNotice))
      .thenApply(noContentRecordInterpreter(scheduledNotice)::apply);
  }

  public CompletableFuture<Result<ScheduledNotice>> delete(ScheduledNotice scheduledNotice) {
    return scheduledNoticesStorageClient.delete(scheduledNotice.getId())
      .thenApply(noContentRecordInterpreter(scheduledNotice)
        .otherwise(forwardOnFailure())::apply);
  }

  CompletableFuture<Result<Void>> deleteByLoanId(String loanId) {
    return CqlQuery.exactMatch("loanId", loanId).after(this::deleteMany);
  }

  private CompletableFuture<Result<Void>> deleteMany(CqlQuery cqlQuery) {
    return scheduledNoticesStorageClient.deleteMany(cqlQuery).thenApply(
      result -> result.next(response -> {
        if (response.getStatusCode() == 204) {
          return succeeded(null);
        } else {
          return failed(new ForwardOnFailure(response));
        }
      }));
  }
}
