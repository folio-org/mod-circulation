package org.folio.circulation.resources;

import static java.lang.Math.max;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.GroupedScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticeGroupDefinition;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.CqlSortClause;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;

public abstract class NotRealTimeScheduledNoticeProcessingResource
  extends ScheduledNoticeProcessingResource {

  private final EnumSet<TriggeringEvent> triggeringEvents;

  private static final CqlSortBy FETCH_NOTICES_SORT_CLAUSE =
    CqlSortBy.sortBy(
      Stream.of(
        "recipientUserId", "noticeConfig.templateId",
        "triggeringEvent", "noticeConfig.format",
        "noticeConfig.timing")
        .map(CqlSortClause::ascending)
        .collect(toList())
    );

  protected NotRealTimeScheduledNoticeProcessingResource(HttpClient client, String rootPath,
    TriggeringEvent triggeringEvent) {

    this(client, rootPath, EnumSet.of(triggeringEvent));
  }

  protected NotRealTimeScheduledNoticeProcessingResource(HttpClient client, String rootPath,
    EnumSet<TriggeringEvent> triggeringEvents) {

    super(rootPath, client);
    this.triggeringEvents = triggeringEvents;
  }

  protected abstract GroupedScheduledNoticeHandler getHandler(Clients clients,
    LoanRepository loanRepository);

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ConfigurationRepository configurationRepository,
    ScheduledNoticesRepository scheduledNoticesRepository, PageLimit pageLimit) {

    return configurationRepository.findTimeZoneConfiguration()
      .thenApply(r -> r.map(this::startOfTodayInTimeZone))
      .thenCompose(r -> r.after(timeLimit -> findNotices(scheduledNoticesRepository,
        pageLimit, timeLimit)));
  }

  private ZonedDateTime startOfTodayInTimeZone(ZoneId zone) {
    return atStartOfDay(getZonedDateTime().withZoneSameInstant(zone));
  }

  private CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNotices(
    ScheduledNoticesRepository scheduledNoticesRepository, PageLimit pageLimit,
    ZonedDateTime timeLimit) {

    return scheduledNoticesRepository.findNotices(timeLimit, false, triggeringEvents,
      FETCH_NOTICES_SORT_CLAUSE, pageLimit);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(
    Clients clients, RequestRepository requestRepository,
    LoanRepository loanRepository, MultipleRecords<ScheduledNotice> notices) {

    return getHandler(clients, loanRepository)
      .handleNotices(groupNotices(notices))
      .thenApply(mapResult(v -> notices));
  }

  private static List<List<ScheduledNotice>> groupNotices(MultipleRecords<ScheduledNotice> notices) {
    Map<ScheduledNoticeGroupDefinition, List<ScheduledNotice>> orderedGroups = notices.getRecords()
      .stream()
      .collect(groupingBy(ScheduledNoticeGroupDefinition::from, LinkedHashMap::new, toList()));

    boolean fetchedAllTheRecords = notices.getTotalRecords().equals(notices.getRecords().size());
    //If not all the records are fetched then the last group is cut off because there might be only a part of it
    //If there is only one group, it is taken into processing
    int limit = fetchedAllTheRecords
      ? orderedGroups.size()
      : max(orderedGroups.size() - 1, 1);

    return orderedGroups.entrySet()
      .stream()
      .limit(limit)
      .map(Map.Entry::getValue)
      .collect(toList());
  }

}
