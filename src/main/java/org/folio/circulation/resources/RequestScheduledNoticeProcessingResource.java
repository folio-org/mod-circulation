package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.HOLD_EXPIRATION;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.REQUEST_EXPIRATION;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.TITLE_LEVEL_REQUEST_EXPIRATION;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.ItemLevelRequestScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.TitleLevelRequestScheduledNoticeHandler;
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

public class RequestScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {

  public RequestScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/request-scheduled-notices-processing", client);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ConfigurationRepository configurationRepository,
    ScheduledNoticesRepository scheduledNoticesRepository, PageLimit pageLimit) {

    return scheduledNoticesRepository.findNotices(ClockUtil.getZonedDateTime(), true,
      Arrays.asList(HOLD_EXPIRATION, REQUEST_EXPIRATION, TITLE_LEVEL_REQUEST_EXPIRATION),
      CqlSortBy.ascending("nextRunTime"), pageLimit);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(
    Clients clients, RequestRepository requestRepository, LoanRepository loanRepository,
    MultipleRecords<ScheduledNotice> scheduledNotices) {

    Collection<ScheduledNotice> records = scheduledNotices.getRecords();
    Map<Boolean, List<ScheduledNotice>> noticesByRequestLevel = records
      .stream()
      .collect(Collectors.groupingBy(this::isTitleLevelRequestNotice));

    return handleItemLevelRequestNotices(clients,
      noticesByRequestLevel.get(false), requestRepository, loanRepository)
      .thenCompose(v -> handleTitleLevelRequestNotices(clients,
        requestRepository, loanRepository, noticesByRequestLevel.get(true)))
      .thenApply(mapResult(v -> scheduledNotices));
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleItemLevelRequestNotices(
    Clients clients, List<ScheduledNotice> itemLevelNotices, RequestRepository requestRepository,
    LoanRepository loanRepository) {

    if (itemLevelNotices == null || itemLevelNotices.isEmpty()) {
      return ofAsync(() -> null);
    }

    return new ItemLevelRequestScheduledNoticeHandler(clients, requestRepository, loanRepository)
      .handleNotices(itemLevelNotices);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleTitleLevelRequestNotices(
    Clients clients, RequestRepository requestRepository, LoanRepository loanRepository,
    List<ScheduledNotice> titleLevelNotices) {

    if (titleLevelNotices == null || titleLevelNotices.isEmpty()) {
      return ofAsync(() -> null);
    }

    return new TitleLevelRequestScheduledNoticeHandler(clients, requestRepository, loanRepository)
      .handleNotices(titleLevelNotices);
  }

  private boolean isTitleLevelRequestNotice(ScheduledNotice notice) {
    return notice.getTriggeringEvent() == TITLE_LEVEL_REQUEST_EXPIRATION;
  }
}
