package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.schedule.JsonScheduledNoticeMapper.mapFromJson;
import static org.folio.circulation.domain.notice.schedule.JsonScheduledNoticeMapper.mapToJson;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class ScheduledNoticesRepository {

  private static final Logger log = LoggerFactory.getLogger(ScheduledNoticesRepository.class);

  public static ScheduledNoticesRepository using(Clients clients) {
    return new ScheduledNoticesRepository(
      clients.scheduledNoticesStorageClient());
  }

  private final CollectionResourceClient scheduledNoticesStorageClient;


  public ScheduledNoticesRepository(
    CollectionResourceClient scheduledNoticesStorageClient) {
    this.scheduledNoticesStorageClient = scheduledNoticesStorageClient;
  }

  public CompletableFuture<Result<ScheduledNotice>> create(ScheduledNotice scheduledNotice) {
    JsonObject representation = mapToJson(scheduledNotice);
    return scheduledNoticesStorageClient.post(representation).thenApply(response -> {
      if (response.getStatusCode() == 201) {
        return mapFromJson(response.getJson());
      } else {
        log.error("Failed to create scheduled notice. Status: {} Body: {}",
          response.getStatusCode(),
          response.getBody());
        return failed(new ForwardOnFailure(response));
      }
    });
  }

  public CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    DateTime systemTime, int pageLimit) {
    return CqlQuery.lessThan("nextRunTime", systemTime.withZone(DateTimeZone.UTC))
      .after(query -> findBy(query, pageLimit));
  }

  public CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findBy(
    CqlQuery cqlQuery, int pageLimit) {
    return scheduledNoticesStorageClient.getMany(cqlQuery, pageLimit)
      .thenApply(r -> r.next(response ->
        MultipleRecords.from(response, Function.identity(), "scheduledNotices")))
      .thenApply(r -> r.next(records -> records.map(JsonScheduledNoticeMapper::mapFromJson)));
  }

  public CompletableFuture<Result<ScheduledNotice>> update(ScheduledNotice scheduledNotice) {
    return scheduledNoticesStorageClient.put(scheduledNotice.getId(), mapToJson(scheduledNotice))
      .thenApply(response -> {
        if (response.getStatusCode() == 204) {
          return succeeded(scheduledNotice);
        } else {
          return failed(
            new ServerErrorFailure(String.format("Failed to update scheduled notice (%s:%s)",
              response.getStatusCode(), response.getBody())));
        }
      });
  }

  public CompletableFuture<Result<ScheduledNotice>> delete(ScheduledNotice scheduledNotice) {
    return scheduledNoticesStorageClient.delete(scheduledNotice.getId())
      .thenApply(response -> {
        if (response.getStatusCode() == 204) {
          return succeeded(scheduledNotice);
        } else {
          return failed(new ForwardOnFailure(response));
        }
      });
  }
}
