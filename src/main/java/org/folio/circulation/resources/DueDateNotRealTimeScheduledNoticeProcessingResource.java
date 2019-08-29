package org.folio.circulation.resources;

import static java.lang.Math.max;
import static org.folio.circulation.support.ResultBinding.mapResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.DueDateNotRealTimeScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticeGroupDefinition;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticesRepository;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlOrder;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import io.vertx.core.http.HttpClient;

public class DueDateNotRealTimeScheduledNoticeProcessingResource extends ScheduledNoticeProcessingResource {

  public DueDateNotRealTimeScheduledNoticeProcessingResource(HttpClient client) {
    super("/circulation/due-date-not-real-time-scheduled-notices-processing", client);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ScheduledNoticesRepository scheduledNoticesRepository, int limit) {

    List<CqlOrder> cqlOrders = Stream.of(
      "recipientUserId", "noticeConfig.templateId",
      "triggeringEvent", "noticeConfig.format",
      "noticeConfig.timing")
      .map(CqlOrder::asc).collect(Collectors.toList());

    DateTime timeLimit = LocalDate.now().toDateTime(LocalTime.MIDNIGHT);
    return scheduledNoticesRepository.findNotices(timeLimit, false,
      Collections.singletonList(TriggeringEvent.DUE_DATE),
      CqlSortBy.sortBy(cqlOrders), limit);
  }

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(
    Clients clients, MultipleRecords<ScheduledNotice> notices) {

    final DueDateNotRealTimeScheduledNoticeHandler dueDateNoticeHandler =
      DueDateNotRealTimeScheduledNoticeHandler.using(clients, DateTime.now(DateTimeZone.UTC));

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

    List<Pair<ScheduledNoticeGroupDefinition, List<ScheduledNotice>>> noticeGroups =
      orderedGroups.entrySet()
        .stream()
        .limit(limit)
        .map(e -> Pair.of(e.getKey(), e.getValue()))
        .collect(Collectors.toList());

    return dueDateNoticeHandler.handleNotices(noticeGroups)
      .thenApply(mapResult(v -> notices));
  }
}
