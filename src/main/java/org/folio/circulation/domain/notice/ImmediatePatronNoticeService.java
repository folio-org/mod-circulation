package org.folio.circulation.domain.notice;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.notice.combiner.NoticeContextCombiner;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;

public class ImmediatePatronNoticeService extends PatronNoticeService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final PatronNoticePolicyRepository noticePolicyRepository;
  private final NoticeContextCombiner noticeContextCombiner;

  public ImmediatePatronNoticeService(Clients clients, NoticeContextCombiner noticeContextCombiner) {
    super(clients);
    this.noticePolicyRepository = new PatronNoticePolicyRepository(clients);
    this.noticeContextCombiner = noticeContextCombiner;
  }

  public CompletableFuture<Result<Void>> acceptNoticeEvent(PatronNoticeEvent event) {
    log.debug("acceptNoticeEvent:: accepting single notice event");
    return acceptNoticeEvents(singletonList(event));
  }

  public CompletableFuture<Result<Void>> acceptNoticeEvents(Collection<PatronNoticeEvent> events) {
    log.debug("acceptNoticeEvents:: accepting {} notice events", events.size());
    return allOf(events, this::fetchNoticePolicyId)
      .thenApply(mapResult(this::groupEvents))
      .thenCompose(r -> r.after(this::handleEventGroups));
  }

  private CompletableFuture<Result<PatronNoticeEvent>> fetchNoticePolicyId(PatronNoticeEvent event) {
    return ofAsync(() -> event)
      .thenCompose(r -> r.after(noticePolicyRepository::lookupPolicyId))
      .thenApply(mapResult(CirculationRuleMatch::getPolicyId))
      .thenApply(mapResult(event::withPatronNoticePolicyId));
  }

  private List<EventGroupContext> groupEvents(List<PatronNoticeEvent> events) {
    log.info("groupEvents:: grouping {} notice events", events.size());
    return events.stream()
      .collect(groupingBy(NoticeEventGroupDefinition::from))
      .entrySet()
      .stream()
      .map(EventGroupContext::from)
      .map(groupContext -> groupContext.combineContexts(noticeContextCombiner))
      .collect(toList());
  }

  private CompletableFuture<Result<Void>> handleEventGroups(List<EventGroupContext> contexts) {
    return allOf(contexts, this::handleEventGroup)
      .thenApply(mapResult(ignored -> null));
  }

  private CompletableFuture<Result<Void>> handleEventGroup(EventGroupContext context) {
    return ofAsync(() -> context)
      .thenCompose(r -> r.after(this::fetchPatronNoticePolicy))
      .thenCompose(r -> r.after(this::applyPatronNoticePolicy));
  }

  private CompletableFuture<Result<EventGroupContext>> fetchPatronNoticePolicy(
    EventGroupContext context) {

    return noticePolicyRepository.lookupPolicy(context.getGroupDefinition().getNoticePolicyId(),
      new AppliedRuleConditions(false, false, false))
      .thenApply(mapResult(context::withPatronNoticePolicy));
  }

  private CompletableFuture<Result<Void>> applyPatronNoticePolicy(EventGroupContext context) {
    return findMatchingNoticeConfiguration(context)
      .map(context::withNoticeConfig)
      .map(this::updateNoticeLogContext)
      .map(this::sendNotice)
      .orElseGet(() -> ofAsync(() -> null));
  }

  private Optional<NoticeConfiguration> findMatchingNoticeConfiguration(EventGroupContext context) {
    return context.getPatronNoticePolicy().lookupNoticeConfiguration(
      context.getGroupDefinition().getEventType());
  }

  private EventGroupContext updateNoticeLogContext(EventGroupContext context) {
    return context.withCombinedNoticeLogContext(
      context.getCombinedNoticeLogContext()
        .withNoticePolicyId(context.getGroupDefinition().getNoticePolicyId())
        .withTemplateId(context.getNoticeConfig().getTemplateId())
        .withTriggeringEvent(context.getNoticeConfig().getNoticeEventType().getRepresentation()));
  }

  private CompletableFuture<Result<Void>> sendNotice(EventGroupContext context) {
    PatronNotice patronNotice = new PatronNotice(
      context.getGroupDefinition().getRecipientId(),
      context.getCombinedNoticeContext(),
      context.getNoticeConfig());

    return sendNotice(patronNotice, context.getCombinedNoticeLogContext());
  }

  @With
  @Getter
  @AllArgsConstructor
  @RequiredArgsConstructor
  private static class EventGroupContext {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    private final NoticeEventGroupDefinition groupDefinition;
    private final List<PatronNoticeEvent> events;
    private JsonObject combinedNoticeContext;
    private NoticeLogContext combinedNoticeLogContext;
    private NoticeConfiguration noticeConfig;
    private PatronNoticePolicy patronNoticePolicy;

    public static EventGroupContext from(
      Map.Entry<NoticeEventGroupDefinition, List<PatronNoticeEvent>> groupedEvents) {

      return new EventGroupContext(groupedEvents.getKey(), groupedEvents.getValue());
    }

    public EventGroupContext combineContexts(NoticeContextCombiner contextCombiner) {
      return withCombinedNoticeContext(contextCombiner.buildCombinedNoticeContext(events))
        .withCombinedNoticeLogContext(contextCombiner.buildCombinedNoticeLogContext(events));
    }
  }

  @EqualsAndHashCode
  @RequiredArgsConstructor
  @Getter
  private static class NoticeEventGroupDefinition {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    private final String recipientId;
    private final String noticePolicyId;
    private final NoticeEventType eventType;

    private static NoticeEventGroupDefinition from(PatronNoticeEvent event) {
      return new NoticeEventGroupDefinition(
        event.getRecipientId(),
        event.getPatronNoticePolicyId(),
        event.getEventType());
    }
  }

}
