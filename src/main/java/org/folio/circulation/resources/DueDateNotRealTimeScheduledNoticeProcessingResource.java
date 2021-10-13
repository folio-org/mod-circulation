package org.folio.circulation.resources;

import static java.lang.Math.max;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.GroupedLoanScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticeGroupDefinition;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.CqlSortClause;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;

public class DueDateNotRealTimeScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {

  private static final CqlSortBy FETCH_NOTICES_SORT_CLAUSE =
    CqlSortBy.sortBy(
      Stream.of(
        "recipientUserId", "noticeConfig.templateId",
        "triggeringEvent", "noticeConfig.format",
        "noticeConfig.timing")
        .map(CqlSortClause::ascending)
        .collect(Collectors.toList())
    );

  public DueDateNotRealTimeScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/due-date-not-real-time-scheduled-notices-processing", client);
  }

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

    return scheduledNoticesRepository.findNotices(timeLimit,
      false, Collections.singletonList(TriggeringEvent.DUE_DATE),
      FETCH_NOTICES_SORT_CLAUSE, pageLimit);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(
    Clients clients, MultipleRecords<ScheduledNotice> notices) {

    Map<ScheduledNoticeGroupDefinition, List<ScheduledNotice>> orderedGroups =
      notices.getRecords().stream().collect(Collectors.groupingBy(
        ScheduledNoticeGroupDefinition::from,
        LinkedHashMap::new,
        Collectors.toList()));

    boolean fetchedAllTheRecords = notices.getTotalRecords().equals(notices.getRecords().size());
    //If not all the records are fetched then the last group is cut off because there might be only a part of it
    //If there is only one group, it is taken into processing
    int limit = fetchedAllTheRecords
      ? orderedGroups.size()
      : max(orderedGroups.size() - 1, 1);

    List<List<ScheduledNotice>> noticeGroups = orderedGroups.entrySet()
      .stream()
      .limit(limit)
      .map(Map.Entry::getValue)
      .collect(Collectors.toList());

    return new GroupedLoanScheduledNoticeHandler(clients, getZonedDateTime())
      .handleNotices(noticeGroups)
      .thenApply(mapResult(v -> notices));
  }
}
