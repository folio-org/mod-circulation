package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.schedule.JsonScheduledNoticeMapper.mapFromJson;
import static org.folio.circulation.support.Result.failed;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;
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
    JsonObject representation = JsonScheduledNoticeMapper.mapToJson(scheduledNotice);
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

    return CqlQuery.lessThan("nextRunTime", System.currentTimeMillis())
      .after(query -> scheduledNoticeStorageClient.getMany(query, 1000))
      .thenApply(r -> r.next(this::mapResponseToScheduledNotices));
  }

  private Result<MultipleRecords<ScheduledNotice>> mapResponseToScheduledNotices(Response response) {
    return MultipleRecords.from(response, JsonScheduledNoticeMapper::mapFromJson, "scheduledNotices");
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
