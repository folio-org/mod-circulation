package org.folio.circulation.resources;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.RequestScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticesRepository;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.http.HttpClient;

public class RequestScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {

  public RequestScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/request-scheduled-notices-processing" ,client);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ScheduledNoticesRepository scheduledNoticesRepository, int limit) {

    return scheduledNoticesRepository.findNotices(
      DateTime.now(DateTimeZone.UTC), true,
      Arrays.asList(TriggeringEvent.HOLD_EXPIRATION, TriggeringEvent.REQUEST_EXPIRATION),
      CqlSortBy.ascending("nextRunTime"), limit);
  }

  @Override
  protected CompletableFuture<Result<Collection<ScheduledNotice>>> handleNotices(
    Clients clients, Collection<ScheduledNotice> scheduledNotices) {

    return RequestScheduledNoticeHandler.using(clients).handleNotices(scheduledNotices);
  }
}
