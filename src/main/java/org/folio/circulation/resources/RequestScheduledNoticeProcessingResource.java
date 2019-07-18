package org.folio.circulation.resources;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.RequestScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticesRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.http.HttpClient;

public class RequestScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {

  public RequestScheduledNoticeProcessingResource(String rootPath, HttpClient client, int noticesProcessingLimit) {
    super(rootPath ,client, noticesProcessingLimit);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ScheduledNoticesRepository scheduledNoticesRepository) {

    final DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    return scheduledNoticesRepository.findRequestNoticesToSend(systemTime, noticesProcessingLimit);
  }

  @Override
  protected CompletableFuture<Result<Collection<ScheduledNotice>>> handleNotices(
    Clients clients, Collection<ScheduledNotice> scheduledNotices) {

    final RequestScheduledNoticeHandler requestScheduledNoticeHandler =
      RequestScheduledNoticeHandler.using(clients);

    return requestScheduledNoticeHandler.handleNotices(scheduledNotices);
  }
}
