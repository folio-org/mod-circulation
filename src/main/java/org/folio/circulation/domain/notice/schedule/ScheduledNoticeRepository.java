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

public class ScheduledNoticeRepository {

  private static final Logger log = LoggerFactory.getLogger(ScheduledNoticeRepository.class);

  public static ScheduledNoticeRepository using(Clients clients) {
    return new ScheduledNoticeRepository(
      clients.scheduledNoticeStorageClient());
  }

  private final CollectionResourceClient scheduledNoticeStorageClient;


  public ScheduledNoticeRepository(
    CollectionResourceClient scheduledNoticeStorageClient) {
    this.scheduledNoticeStorageClient = scheduledNoticeStorageClient;
  }

  public CompletableFuture<Result<ScheduledNotice>> create(ScheduledNotice scheduledNotice) {
    JsonObject representation = mapToJson(scheduledNotice);
    return scheduledNoticeStorageClient.post(representation).thenApply(response -> {
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

  public CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findScheduledNoticesWithNextRunTimeLessThanNow() {
    return CqlQuery.lessThan("nextRunTime", DateTime.now(DateTimeZone.UTC))
      .after(query -> scheduledNoticeStorageClient.getMany(query, 1000))
      .thenApply(r -> r.next(response ->
        MultipleRecords.from(response, Function.identity(), "scheduledNotices")))
      .thenApply(r -> r.next(records -> records.map(JsonScheduledNoticeMapper::mapFromJson)));
  }

  public CompletableFuture<Result<ScheduledNotice>> update(ScheduledNotice scheduledNotice) {
    return scheduledNoticeStorageClient.put(scheduledNotice.getId(), mapToJson(scheduledNotice))
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
    return scheduledNoticeStorageClient.delete(scheduledNotice.getId())
      .thenApply(response -> {
        if (response.getStatusCode() == 204) {
          return succeeded(scheduledNotice);
        } else {
          return failed(new ForwardOnFailure(response));
        }
      });
  }
}
