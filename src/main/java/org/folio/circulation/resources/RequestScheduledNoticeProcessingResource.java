package org.folio.circulation.resources;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.HOLD_EXPIRATION;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.REQUEST_EXPIRATION;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.TITLE_LEVEL_REQUEST_EXPIRATION;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.notice.schedule.InstanceAwareRequestScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ItemAwareRequestScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticeContext;
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

public class RequestScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public RequestScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/request-scheduled-notices-processing", client);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    SettingsRepository settingsRepository,
    ScheduledNoticesRepository scheduledNoticesRepository,
    PatronActionSessionRepository patronActionSessionRepository, PageLimit pageLimit) {

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
      .collect(toSet());

    return requestRepository.fetchRequests(requestIds)
      .thenCompose(r -> r.after(requests -> handleNotices(clients, requestRepository,
        loanRepository, notices, requests)))
      .thenApply(mapResult(v -> scheduledNoticesRecords));
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleNotices(
    Clients clients, RequestRepository requestRepository, LoanRepository loanRepository,
    Collection<ScheduledNotice> notices, Collection<Request> requests) {

    log.debug("handleNotices:: parameters notices: {}, requests: {}", () ->
      collectionAsString(notices), () -> collectionAsString(requests));
    Map<String, Request> requestsById = requests.stream()
      .collect(toMap(Request::getId, identity()));

    Map<Boolean, List<ScheduledNoticeContext>> groupedContexts = notices.stream()
      .map(notice -> new ScheduledNoticeContext(notice)
        .withRequest(requestsById.get(notice.getRequestId())))
      .collect(groupingBy(RequestScheduledNoticeProcessingResource::requestHasItemId));

    return handleNoticesForRequestsWithItemId(clients, requestRepository, loanRepository,
      groupedContexts.get(true))
      .thenCompose(v -> handleNoticesForRequestsWithoutItemId(clients, requestRepository,
        loanRepository, groupedContexts.get(false)));
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleNoticesForRequestsWithItemId(
    Clients clients, RequestRepository requestRepository, LoanRepository loanRepository,
    Collection<ScheduledNoticeContext> contexts) {

    if (contexts == null || contexts.isEmpty()) {
      return ofAsync(() -> null);
    }

    return new ItemAwareRequestScheduledNoticeHandler(clients, requestRepository, loanRepository)
      .handleContexts(contexts);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleNoticesForRequestsWithoutItemId(
    Clients clients, RequestRepository requestRepository, LoanRepository loanRepository,
    Collection<ScheduledNoticeContext> contexts) {

    if (contexts == null || contexts.isEmpty()) {
      return ofAsync(() -> null);
    }

    return new InstanceAwareRequestScheduledNoticeHandler(clients, requestRepository, loanRepository)
      .handleContexts(contexts);
  }

  private static boolean requestHasItemId(ScheduledNoticeContext context) {
    return Optional.ofNullable(context.getRequest())
      .map(Request::hasItemId)
      .orElse(false);
  }

}
