package org.folio.circulation.domain.notice.schedule;

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

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ScheduledNoticeRepository {

  private static final Logger log = LoggerFactory.getLogger(ScheduledNoticeRepository.class);

  public static ScheduledNoticeRepository using(Clients clients) {
    return new ScheduledNoticeRepository(
      clients.scheduledNoticesBulkClient());
  }

  private final CollectionResourceClient scheduledNoticesBulkClient;


  public ScheduledNoticeRepository(
    CollectionResourceClient scheduledNoticesBulkClient) {

    this.scheduledNoticesBulkClient = scheduledNoticesBulkClient;
  }

  public CompletableFuture<Result<List<ScheduledNotice>>> createBatch(
    List<ScheduledNotice> scheduledNotices) {

    List<JsonObject> noticesList = scheduledNotices.stream()
      .map(JsonScheduledNoticeMapper::mapToJson)
      .collect(Collectors.toList());
    JsonObject representation = new JsonObject()
      .put("scheduledNotices", new JsonArray(noticesList));

    return scheduledNoticesBulkClient.post(representation).thenApply(response -> {
      if (response.getStatusCode() == 201) {
        return succeeded(scheduledNotices);
      } else {
        log.error("Failed to save scheduled notices. Status: {} Body: {}",
          response.getStatusCode(),
          response.getBody());
        return failed(new ForwardOnFailure(response));
      }
    });
  }

}
