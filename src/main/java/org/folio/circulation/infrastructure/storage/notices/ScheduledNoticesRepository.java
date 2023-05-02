package org.folio.circulation.infrastructure.storage.notices;

import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static java.util.function.Function.identity;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.circulation.domain.notice.NoticeTiming.AFTER;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.DUE_DATE;
import static org.folio.circulation.infrastructure.storage.notices.JsonScheduledNoticeMapper.LOAN_ID;
import static org.folio.circulation.infrastructure.storage.notices.JsonScheduledNoticeMapper.NOTICE_CONFIG;
import static org.folio.circulation.infrastructure.storage.notices.JsonScheduledNoticeMapper.TIMING;
import static org.folio.circulation.infrastructure.storage.notices.JsonScheduledNoticeMapper.TRIGGERING_EVENT;
import static org.folio.circulation.infrastructure.storage.notices.JsonScheduledNoticeMapper.mapToJson;
import static org.folio.circulation.support.http.CommonResponseInterpreters.noContentRecordInterpreter;
import static org.folio.circulation.support.http.ResponseMapping.flatMapUsingJson;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.logging.PatronNoticeLogHelper.logResponse;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class ScheduledNoticesRepository {
  private static final List<String> UPON_AT_AND_AFTER_TIMING =
    Arrays.asList(UPON_AT.getRepresentation(), AFTER.getRepresentation());

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
      .whenComplete((r, t) -> logResponse(r, t, HTTP_CREATED.toInt(), POST, scheduledNotice))
      .thenApply(interpreter::flatMap);
  }

  public CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNotices(
    ZonedDateTime timeLimit, boolean realTime, Collection<TriggeringEvent> triggeringEvents,
    CqlSortBy cqlSortBy, PageLimit pageLimit) {

    List<String> triggeringEventRepresentations = triggeringEvents.stream()
      .map(TriggeringEvent::getRepresentation)
      .collect(Collectors.toList());

    return CqlQuery.lessThan("nextRunTime", formatDateTime(timeLimit.withZoneSameInstant(ZoneOffset.UTC)))
      .combine(exactMatch("noticeConfig.sendInRealTime", Boolean.toString(realTime)), CqlQuery::and)
      .combine(exactMatchAny("triggeringEvent", triggeringEventRepresentations), CqlQuery::and)
      .map(cqlQuery -> cqlQuery.sortBy(cqlSortBy))
      .after(query -> findBy(query, pageLimit));
  }

  private CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findBy(
    CqlQuery cqlQuery, PageLimit pageLimit) {

    return scheduledNoticesStorageClient.getMany(cqlQuery, pageLimit)
      .whenComplete((r, t) -> logResponse(r, t, HTTP_OK.toInt(), GET, cqlQuery))
      .thenApply(r -> r.next(response ->
        MultipleRecords.from(response, identity(), "scheduledNotices")))
      .thenApply(r -> r.next(records -> records.flatMapRecords(
        JsonScheduledNoticeMapper::mapFromJson)));
  }

  public CompletableFuture<Result<ScheduledNotice>> update(
    ScheduledNotice scheduledNotice) {

    return scheduledNoticesStorageClient.put(scheduledNotice.getId(),
        mapToJson(scheduledNotice))
      .whenComplete((r, t) -> logResponse(r, t, HTTP_NO_CONTENT.toInt(), PUT, scheduledNotice))
      .thenApply(noContentRecordInterpreter(scheduledNotice)::flatMap);
  }

  public CompletableFuture<Result<ScheduledNotice>> delete(
    ScheduledNotice scheduledNotice) {

    final ResponseInterpreter<ScheduledNotice> interpreter
      = noContentRecordInterpreter(scheduledNotice)
      .otherwise(forwardOnFailure());

    return scheduledNoticesStorageClient.delete(scheduledNotice.getId())
      .whenComplete((r, t) -> logResponse(r, t, HTTP_NO_CONTENT.toInt(), DELETE, scheduledNotice))
      .thenApply(flatMapResult(interpreter::apply));
  }

  public CompletableFuture<Result<Response>> deleteByLoanIdAndTriggeringEvent(
    String loanId, TriggeringEvent triggeringEvent) {

    return exactMatch("loanId", loanId)
      .combine(exactMatch("triggeringEvent", triggeringEvent.getRepresentation()), CqlQuery::and)
      .after(this::deleteMany);
  }

  public CompletableFuture<Result<Response>> deleteOverdueNotices(String loanId) {

    return exactMatch(LOAN_ID, loanId)
      .combine(exactMatch(TRIGGERING_EVENT, DUE_DATE.getRepresentation()), CqlQuery::and)
      .combine(exactMatchAny(NOTICE_CONFIG + "." + TIMING, UPON_AT_AND_AFTER_TIMING), CqlQuery::and)
      .after(this::deleteMany);
  }

  public CompletableFuture<Result<Response>> deleteByRequestId(String requestId) {
    return exactMatch("requestId", requestId).after(this::deleteMany);
  }

  private CompletableFuture<Result<Response>> deleteMany(CqlQuery cqlQuery) {
    final ResponseInterpreter<Response> interpreter = new ResponseInterpreter<Response>()
      .flatMapOn(204, Result::succeeded)
      .otherwise(forwardOnFailure());

    return scheduledNoticesStorageClient.deleteMany(cqlQuery)
      .whenComplete((r, t) -> logResponse(r, t, HTTP_NO_CONTENT.toInt(), DELETE, cqlQuery))
      .thenApply(responseResult -> responseResult.next(interpreter::apply));
  }
}
