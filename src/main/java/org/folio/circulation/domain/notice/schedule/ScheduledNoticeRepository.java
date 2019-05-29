package org.folio.circulation.domain.notice.schedule;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
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

  public void create(ScheduledNotice scheduledNotice) {
    JsonObject representation = JsonScheduledNoticeMapper.mapToJson(scheduledNotice);
    scheduledNoticeStorageClient.post(representation).thenAccept(response -> {
      if (response.getStatusCode() == 201) {
        log.error("Failed to create scheduled notice. Status: {} Body: {}",
          response.getStatusCode(),
          response.getBody());
      }
    });
  }
}
