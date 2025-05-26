package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST_FINE_CHARGED;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST_RETURNED;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.sessions.PatronActionSessionRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.http.HttpClient;

public class FeeFineScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {
  private static final List<TriggeringEvent> TRIGGERING_EVENTS_TO_PROCESS = List.of(
    AGED_TO_LOST_FINE_CHARGED,
    AGED_TO_LOST_RETURNED
  );

  public FeeFineScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/fee-fine-scheduled-notices-processing", client);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    SettingsRepository settingsRepository,
    ScheduledNoticesRepository scheduledNoticesRepository,
    PatronActionSessionRepository patronActionSessionRepository, PageLimit pageLimit) {

    return scheduledNoticesRepository.findNotices(
      ClockUtil.getZonedDateTime(), true, TRIGGERING_EVENTS_TO_PROCESS,
      CqlSortBy.ascending("nextRunTime"), pageLimit);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(
    Clients clients,
    RequestRepository requestRepository,
    LoanRepository loanRepository,
    MultipleRecords<ScheduledNotice> scheduledNotices) {

    return new FeeFineScheduledNoticeHandler(clients, loanRepository)
      .handleNotices(scheduledNotices.getRecords())
      .thenApply(mapResult(v -> scheduledNotices));
  }
}
