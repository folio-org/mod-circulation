package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST_FINE_CHARGED;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST_RETURNED;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.OVERDUE_FINE_RENEWED;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.OVERDUE_FINE_RETURNED;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.http.HttpClient;

public class FeeFineScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {
  private static final List<TriggeringEvent> TRIGGERING_EVENTS_TO_PROCESS = List.of(
    OVERDUE_FINE_RETURNED,
    OVERDUE_FINE_RENEWED,
    AGED_TO_LOST_FINE_CHARGED,
    AGED_TO_LOST_RETURNED
  );

  public FeeFineScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/fee-fine-scheduled-notices-processing", client);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ConfigurationRepository configurationRepository,
    ScheduledNoticesRepository scheduledNoticesRepository, PageLimit pageLimit) {

    return scheduledNoticesRepository.findNotices(
      ClockUtil.getDateTime(), true, TRIGGERING_EVENTS_TO_PROCESS,
      CqlSortBy.ascending("nextRunTime"), pageLimit);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(
    Clients clients, MultipleRecords<ScheduledNotice> scheduledNotices) {

    return new FeeFineScheduledNoticeHandler(clients)
      .handleNotices(scheduledNotices.getRecords())
      .thenApply(mapResult(v -> scheduledNotices));
  }
}
