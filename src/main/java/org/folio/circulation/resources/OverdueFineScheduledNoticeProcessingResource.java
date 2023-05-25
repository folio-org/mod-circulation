package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.OVERDUE_FINE_RENEWED;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.OVERDUE_FINE_RETURNED;
import static org.folio.circulation.support.http.client.PageLimit.oneThousand;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.GroupedFeeFineScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.GroupedScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.session.PatronSessionRecord;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.sessions.PatronActionSessionRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;

public class OverdueFineScheduledNoticeProcessingResource
  extends GroupingScheduledNoticeProcessingResource {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public OverdueFineScheduledNoticeProcessingResource(HttpClient client) {
    super(client, "/circulation/overdue-fine-scheduled-notices-processing",
      EnumSet.of(OVERDUE_FINE_RETURNED, OVERDUE_FINE_RENEWED), true);
  }

  @Override
  protected GroupedScheduledNoticeHandler getHandler(Clients clients, LoanRepository loanRepository) {
    return new GroupedFeeFineScheduledNoticeHandler(clients, loanRepository);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ConfigurationRepository configurationRepository,
    ScheduledNoticesRepository scheduledNoticesRepository,
    PatronActionSessionRepository patronActionSessionRepository, PageLimit pageLimit) {

    return super.findNoticesToSend(configurationRepository, scheduledNoticesRepository,
        patronActionSessionRepository, pageLimit)
      .thenCompose(r -> r.after(notices -> filterNotices(notices, patronActionSessionRepository)));
  }

  private CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> filterNotices(
    MultipleRecords<ScheduledNotice> notices,
    PatronActionSessionRepository patronActionSessionRepository) {

    Set<String> sessionIdsFromNotices = notices.getRecords()
      .stream()
      .map(ScheduledNotice::getSessionId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    log.info("filterNotices:: {} unique session IDs found in {} notices",
      sessionIdsFromNotices.size(), notices.size());

    return patronActionSessionRepository.findPatronActionSessions(sessionIdsFromNotices, oneThousand())
      .thenApply(r -> r.map(sessions -> skipNoticesWithOpenPatronActionSessions(notices, sessions)));
  }

  private static MultipleRecords<ScheduledNotice> skipNoticesWithOpenPatronActionSessions(
    MultipleRecords<ScheduledNotice> notices, Collection<PatronSessionRecord> sessions) {

    log.info("skipNoticesWithOpenPatronActionSessions:: processing {} open sessions", sessions.size());

    if (sessions.isEmpty()) {
      return notices;
    }

    Set<String> openSessionIds = sessions.stream()
      .map(PatronSessionRecord::getSessionId)
      .filter(Objects::nonNull)
      .map(UUID::toString)
      .collect(Collectors.toSet());

    return notices.filter(notice -> !openSessionIds.contains(notice.getSessionId()));
  }

}
