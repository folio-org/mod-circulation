package org.folio.circulation.resources;

import static java.util.stream.Collectors.groupingBy;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.HOLD_EXPIRATION;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.REQUEST_EXPIRATION;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.TITLE_LEVEL_REQUEST_EXPIRATION;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.notice.schedule.InstanceAwareRequestScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ItemAwareRequestScheduledNoticeHandler;
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
    MultipleRecords<ScheduledNotice> scheduledNoticesRecords) {

    Collection<ScheduledNotice> notices = scheduledNoticesRecords.getRecords();

    Set<String> requestIds = notices.stream()
      .map(ScheduledNotice::getRequestId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    // TODO: avoid fetching requests twice
    return requestRepository.fetchRequests(requestIds)
      .thenCompose(r -> r.after(requests -> handleNotices(clients, requestRepository,
        loanRepository, notices, requests)))
      .thenApply(mapResult(v -> scheduledNoticesRecords));
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleNotices(
    Clients clients, RequestRepository requestRepository, LoanRepository loanRepository,
    Collection<ScheduledNotice> notices, Collection<Request> requests) {

    Map<String, Boolean> requestIdToItemIdPresence = requests.stream()
      .collect(Collectors.toMap(Request::getId, Request::hasItemId));

    Map<Boolean, List<ScheduledNotice>> groupedNotices = notices.stream()
      .collect(groupingBy(notice -> requestIdToItemIdPresence.getOrDefault(notice.getRequestId(), false)));

    return handleNoticesForRequestsWithItemId(clients, requestRepository, loanRepository,
      groupedNotices.get(true))
      .thenCompose(v -> handleNoticesForRequestsWithoutItemId(clients, requestRepository,
        loanRepository, groupedNotices.get(false)));
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleNoticesForRequestsWithItemId(
    Clients clients, RequestRepository requestRepository, LoanRepository loanRepository,
    List<ScheduledNotice> notices) {

    if (notices == null || notices.isEmpty()) {
      return ofAsync(() -> null);
    }

    return new ItemAwareRequestScheduledNoticeHandler(clients, requestRepository, loanRepository)
      .handleNotices(notices);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleNoticesForRequestsWithoutItemId(
    Clients clients, RequestRepository requestRepository, LoanRepository loanRepository,
    List<ScheduledNotice> notices) {

    if (notices == null || notices.isEmpty()) {
      return ofAsync(() -> null);
    }

    return new InstanceAwareRequestScheduledNoticeHandler(clients, requestRepository, loanRepository)
      .handleNotices(notices);
  }

}
