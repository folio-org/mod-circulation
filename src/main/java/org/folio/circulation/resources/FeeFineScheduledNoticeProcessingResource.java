package org.folio.circulation.resources;

import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.client.PageLimit;

import io.vertx.core.http.HttpClient;

public class FeeFineScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {

  public FeeFineScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/fee-fine-scheduled-notices-processing", client);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ConfigurationRepository configurationRepository,
    ScheduledNoticesRepository scheduledNoticesRepository, PageLimit pageLimit) {

    return scheduledNoticesRepository.findNotices(
      ClockManager.getClockManager().getDateTime(), true,
      Arrays.asList(TriggeringEvent.OVERDUE_FINE_RETURNED, TriggeringEvent.OVERDUE_FINE_RENEWED),
      CqlSortBy.ascending("nextRunTime"), pageLimit);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(
    Clients clients, MultipleRecords<ScheduledNotice> scheduledNotices) {

    return FeeFineScheduledNoticeHandler.using(clients)
      .handleNotices(scheduledNotices.getRecords())
      .thenApply(mapResult(v -> scheduledNotices));
  }
}
