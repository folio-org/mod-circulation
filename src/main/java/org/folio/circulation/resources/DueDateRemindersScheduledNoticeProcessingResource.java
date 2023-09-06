package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeReminderFeeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.sessions.PatronActionSessionRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.*;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

public class DueDateRemindersScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {
  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public DueDateRemindersScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/due-date-reminders-scheduled-notices-processing", client);
    log.debug("Instantiating reminders processing resource");
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(ConfigurationRepository configurationRepository, ScheduledNoticesRepository scheduledNoticesRepository, PatronActionSessionRepository patronActionSessionRepository, PageLimit pageLimit) {
    return scheduledNoticesRepository.findNotices(
      ClockUtil.getZonedDateTime(), true,
      List.of(DUE_DATE_WITH_REMINDER_FEE),
      CqlSortBy.ascending("nextRunTime"), pageLimit);

  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(Clients clients, RequestRepository requestRepository, LoanRepository loanRepository, MultipleRecords<ScheduledNotice> noticesResult) {
    return new LoanScheduledNoticeReminderFeeHandler(clients, loanRepository)
      .handleNotices(noticesResult.getRecords())
      .thenApply(mapResult(v -> noticesResult));
  }

}
