package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.DUE_DATE;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeHandler;
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

import io.vertx.core.http.HttpClient;

public class LoanScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public LoanScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/loan-scheduled-notices-processing", client);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ConfigurationRepository configurationRepository,
    ScheduledNoticesRepository scheduledNoticesRepository,
    PatronActionSessionRepository patronActionSessionRepository, PageLimit pageLimit) {

    return scheduledNoticesRepository.findNotices(
        ClockUtil.getZonedDateTime(), true, List.of(DUE_DATE, AGED_TO_LOST),
        CqlSortBy.ascending("nextRunTime"), pageLimit)
      .thenApply(r -> r.next(this::logNotices));
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(
    Clients clients,
    RequestRepository requestRepository,
    LoanRepository loanRepository,
    MultipleRecords<ScheduledNotice> noticesResult) {

    log.debug("handleNotices:: parameters noticesResult: {}",
      () -> multipleRecordsAsString(noticesResult));

    return new LoanScheduledNoticeHandler(clients, loanRepository)
      .handleNotices(noticesResult.getRecords())
      .thenApply(mapResult(v -> noticesResult));
  }

  private Result<MultipleRecords<ScheduledNotice>> logNotices(
    MultipleRecords<ScheduledNotice> records) {

    log.info("logNotices:: found notices: {}", () -> multipleRecordsAsString(records));

    return succeeded(records);
  }
}
