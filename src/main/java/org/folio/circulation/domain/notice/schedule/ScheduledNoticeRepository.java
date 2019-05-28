package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.schedule.JsonScheduledNoticeMapper.mapFromJson;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.Result;
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

  private static Result<List<ScheduledNotice>> apply(Result<List<ScheduledNotice>> listResult, Result<List<ScheduledNotice>> listResult2) {
    return listResult;
  }

  public CompletableFuture<Result<ScheduledNotice>> create(ScheduledNotice scheduledNotice) {
    JsonObject representation = JsonScheduledNoticeMapper.mapToJson(scheduledNotice);
    return scheduledNoticeStorageClient.post(representation).thenApply(response -> {
      if (response.getStatusCode() == 201) {
        return succeeded(mapFromJson(response.getJson()));
      } else {
        return failed(new ForwardOnFailure(response));
      }
    });
  }

  public CompletableFuture<Result<List<ScheduledNotice>>> createBatch(
    List<ScheduledNotice> scheduledNotices) {

    List<CompletableFuture<Result<ScheduledNotice>>> futures =
      scheduledNotices.stream()
        .map(this::create)
        .collect(Collectors.toList());
    return Result.allOf(futures);
  }
}
