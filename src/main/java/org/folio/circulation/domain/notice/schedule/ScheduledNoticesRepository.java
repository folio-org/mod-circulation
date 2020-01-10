package org.folio.circulation.domain.notice.schedule;

import static java.util.function.Function.identity;
import static org.folio.circulation.domain.notice.schedule.JsonScheduledNoticeMapper.mapToJson;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.http.CommonResponseInterpreters.noContentRecordInterpreter;
import static org.folio.circulation.support.http.ResponseMapping.flatMapUsingJson;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;
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
      .thenApply(interpreter::apply);
  }

  public CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNotices(
    DateTime timeLimit, boolean realTime,
    List<TriggeringEvent> triggeringEvents, CqlSortBy cqlSortBy, int pageLimit) {

    List<String> triggeringEventRepresentations = triggeringEvents.stream()
      .map(TriggeringEvent::getRepresentation)
      .collect(Collectors.toList());

    return CqlQuery.lessThan("nextRunTime", timeLimit.withZone(DateTimeZone.UTC))
      .combine(exactMatch("noticeConfig.sendInRealTime", Boolean.toString(realTime)), CqlQuery::and)
      .combine(exactMatchAny("triggeringEvent", triggeringEventRepresentations), CqlQuery::and)
      .map(cqlQuery -> cqlQuery.sortBy(cqlSortBy))
      .after(query -> findBy(query, pageLimit));
  }

  private CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findBy(
    CqlQuery cqlQuery, int pageLimit) {

    return scheduledNoticesStorageClient.getMany(cqlQuery, pageLimit)
      .thenApply(r -> r.next(response ->
        MultipleRecords.from(response, identity(), "scheduledNotices")))
      .thenApply(r -> r.next(records -> records.flatMapRecords(
        JsonScheduledNoticeMapper::mapFromJson)));
  }

  public CompletableFuture<Result<ScheduledNotice>> update(ScheduledNotice scheduledNotice) {
    return scheduledNoticesStorageClient.put(scheduledNotice.getId(), mapToJson(scheduledNotice))
      .thenApply(noContentRecordInterpreter(scheduledNotice)::apply);
  }

  public CompletableFuture<Result<ScheduledNotice>> delete(ScheduledNotice scheduledNotice) {
    final ResponseInterpreter<ScheduledNotice> interpreter
      = noContentRecordInterpreter(scheduledNotice)
      .otherwise(forwardOnFailure());

    return scheduledNoticesStorageClient.delete(scheduledNotice.getId())
      .thenApply(interpreter::apply);
  }

  CompletableFuture<Result<Response>> deleteByLoanId(String loanId) {
    return exactMatch("loanId", loanId).after(this::deleteMany);
  }

  CompletableFuture<Result<Response>> deleteByRequestId(String requestId) {
    return exactMatch("requestId", requestId).after(this::deleteMany);
  }

  private CompletableFuture<Result<Response>> deleteMany(CqlQuery cqlQuery) {
    final ResponseInterpreter<Response> interpreter = new ResponseInterpreter<Response>()
      .flatMapOn(204, Result::succeeded)
      .otherwise(forwardOnFailure());

    return scheduledNoticesStorageClient.deleteMany(cqlQuery)
      .thenApply(responseResult -> responseResult.next(interpreter::apply));
  }
}
