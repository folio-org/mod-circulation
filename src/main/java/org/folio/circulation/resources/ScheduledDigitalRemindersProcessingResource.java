package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.ScheduledDigitalReminderHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.sessions.PatronActionSessionRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import java.lang.invoke.MethodHandles;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.*;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;

public class ScheduledDigitalRemindersProcessingResource extends ScheduledNoticeProcessingResource {
  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public ScheduledDigitalRemindersProcessingResource(HttpClient client) {
    super("/circulation/scheduled-digital-reminders-processing", client);
    log.debug("Instantiating digital reminders processing - notices and fees");
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(ConfigurationRepository configurationRepository, ScheduledNoticesRepository scheduledNoticesRepository, PatronActionSessionRepository patronActionSessionRepository, PageLimit pageLimit) {
    return CqlQuery.lessThan("nextRunTime", formatDateTime(ClockUtil.getZonedDateTime().withZoneSameInstant(ZoneOffset.UTC)))
      .combine(exactMatch("noticeConfig.sendInRealTime", "true"), CqlQuery::and)
      .combine(exactMatch("triggeringEvent", DUE_DATE_WITH_REMINDER_FEE.getRepresentation()), CqlQuery::and)
      .combine(exactMatch("noticeConfig.format", "Email"), CqlQuery::and)
      .map(cqlQuery -> cqlQuery.sortBy(CqlSortBy.ascending("nextRunTime")))
      .after(query -> scheduledNoticesRepository.findBy(query, pageLimit));
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(Clients clients, RequestRepository requestRepository, LoanRepository loanRepository, MultipleRecords<ScheduledNotice> noticesResult) {
    return new ScheduledDigitalReminderHandler(clients, loanRepository)
      .handleNotices(noticesResult.getRecords())
      .thenApply(mapResult(v -> noticesResult));
  }
}
