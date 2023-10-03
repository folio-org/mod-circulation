package org.folio.circulation.resources;

import static java.lang.Math.max;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;

import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.GroupedScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.grouping.DefaultScheduledNoticeGroupDefinitionFactory;
import org.folio.circulation.domain.notice.schedule.grouping.ScheduledNoticeGroupDefinition;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;
import org.folio.circulation.domain.notice.schedule.grouping.ScheduledNoticeGroupDefinitionFactory;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.sessions.PatronActionSessionRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.CqlSortClause;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.http.HttpClient;

public abstract class GroupingScheduledNoticeProcessingResource
  extends ScheduledNoticeProcessingResource {

  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final EnumSet<TriggeringEvent> triggeringEvents;
  private final boolean realTime;
  private final ScheduledNoticeGroupDefinitionFactory groupDefinitionFactory;

  private static final CqlSortBy FETCH_NOTICES_SORT_CLAUSE =
    CqlSortBy.sortBy(
      Stream.of(
        "recipientUserId", "noticeConfig.templateId",
        "triggeringEvent", "noticeConfig.format",
        "noticeConfig.timing")
        .map(CqlSortClause::ascending)
        .collect(toList())
    );

  protected GroupingScheduledNoticeProcessingResource(HttpClient client, String rootPath,
    EnumSet<TriggeringEvent> triggeringEvents, boolean realTime) {

    this(client, rootPath, triggeringEvents, realTime,
      new DefaultScheduledNoticeGroupDefinitionFactory());
  }

  protected GroupingScheduledNoticeProcessingResource(HttpClient client, String rootPath,
    EnumSet<TriggeringEvent> triggeringEvents, boolean realTime,
    ScheduledNoticeGroupDefinitionFactory groupDefinitionFactory) {

    super(rootPath, client);
    this.triggeringEvents = triggeringEvents;
    this.realTime = realTime;
    this.groupDefinitionFactory = groupDefinitionFactory;
  }

  protected abstract GroupedScheduledNoticeHandler getHandler(Clients clients,
    LoanRepository loanRepository);

  @Override
  protected CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ConfigurationRepository configurationRepository,
    ScheduledNoticesRepository scheduledNoticesRepository,
    PatronActionSessionRepository patronActionSessionRepository, PageLimit pageLimit) {

    log.debug("findNoticesToSend:: pageLimit: {}", pageLimit.getLimit());

    return getTimeLimit(configurationRepository)
      .thenCompose(r -> r.after(timeLimit -> findNotices(scheduledNoticesRepository,
        pageLimit, timeLimit)));
  }

  private CompletableFuture<Result<ZonedDateTime>> getTimeLimit(
    ConfigurationRepository configurationRepository) {

    log.debug("getTimeLimit:: realTime: {}", realTime);

    if (realTime) {
      return ofAsync(ClockUtil.getZonedDateTime());
    }

    return configurationRepository.findTimeZoneConfiguration()
      .thenApply(r -> r.map(this::startOfTodayInTimeZone));
  }

  private ZonedDateTime startOfTodayInTimeZone(ZoneId zone) {
    return atStartOfDay(getZonedDateTime().withZoneSameInstant(zone));
  }

  private CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNotices(
    ScheduledNoticesRepository scheduledNoticesRepository, PageLimit pageLimit,
    ZonedDateTime timeLimit) {

    log.debug("findNotices:: pageLimit: {}, timeLimit: {}", pageLimit, timeLimit);

    return scheduledNoticesRepository.findNotices(timeLimit, realTime, triggeringEvents,
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

  private List<List<ScheduledNotice>> groupNotices(MultipleRecords<ScheduledNotice> notices) {
    Map<ScheduledNoticeGroupDefinition, List<ScheduledNotice>> orderedGroups = notices.getRecords()
      .stream()
      .collect(groupingBy(groupDefinitionFactory::newInstance, LinkedHashMap::new, toList()));

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
