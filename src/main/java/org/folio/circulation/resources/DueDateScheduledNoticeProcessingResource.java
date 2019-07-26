package org.folio.circulation.resources;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.DueDateScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticesRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.http.HttpClient;

public class DueDateScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {

  private static final int SCHEDULED_NOTICES_PROCESSING_LIMIT = 100;

  public DueDateScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/scheduled-notices-processing", client);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ScheduledNoticesRepository scheduledNoticesRepository) {

    return scheduledNoticesRepository.findNoticesToSend(
      DateTime.now(DateTimeZone.UTC), SCHEDULED_NOTICES_PROCESSING_LIMIT);
  }

  @Override
  protected CompletableFuture<Result<Collection<ScheduledNotice>>> handleNotices(
    Clients clients, Collection<ScheduledNotice> noticesResult) {

    final DueDateScheduledNoticeHandler dueDateNoticeHandler =
      DueDateScheduledNoticeHandler.using(clients, DateTime.now(DateTimeZone.UTC));

    return dueDateNoticeHandler.handleNotices(noticesResult);
  }
}
