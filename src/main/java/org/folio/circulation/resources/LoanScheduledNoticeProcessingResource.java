package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.DUE_DATE;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.http.HttpClient;

public class LoanScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {

  public LoanScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/loan-scheduled-notices-processing", client);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ConfigurationRepository configurationRepository,
    ScheduledNoticesRepository scheduledNoticesRepository, PageLimit pageLimit) {

    return scheduledNoticesRepository.findNotices(
      ClockUtil.getZonedDateTime(), true,
      List.of(DUE_DATE, AGED_TO_LOST),
      CqlSortBy.ascending("nextRunTime"), pageLimit);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(
    Clients clients,
    RequestRepository requestRepository,
    LoanRepository loanRepository,
    MultipleRecords<ScheduledNotice> noticesResult) {

    return new LoanScheduledNoticeHandler(clients, loanRepository)
      .handleNotices(noticesResult.getRecords())
      .thenApply(mapResult(v -> noticesResult));
  }
}
