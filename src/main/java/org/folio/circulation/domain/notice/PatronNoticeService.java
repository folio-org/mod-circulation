package org.folio.circulation.domain.notice;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.http.HttpStatus.SC_OK;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.logging.PatronNoticeLogHelper.logResponse;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.http.CommonResponseInterpreters.mapToRecordInterpreter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticeConfig;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.client.ResponseInterpreter;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

public class PatronNoticeService {
  public static PatronNoticeService using(Clients clients) {
    return new PatronNoticeService(new PatronNoticePolicyRepository(clients), clients);
  }

  private final PatronNoticePolicyRepository noticePolicyRepository;
  private final CollectionResourceClient patronNoticeClient;
  private final EventPublisher eventPublisher;

  public PatronNoticeService(PatronNoticePolicyRepository noticePolicyRepository, Clients clients) {
    this.noticePolicyRepository = noticePolicyRepository;
    this.patronNoticeClient = clients.patronNoticeClient();
    this.eventPublisher = new EventPublisher(clients.pubSubPublishingService());
  }

  public CompletableFuture<Result<Void>> acceptNoticeEvent(PatronNoticeEvent event, NoticeLogContext logContext) {
    return acceptMultipleNoticeEvent(Collections.singletonList(new NoticeEventBundle(event, logContext)),
      contexts -> contexts.stream().findFirst().orElse(new JsonObject()),
      logContexts -> logContexts.stream().findFirst().orElse(new NoticeLogContext()));
  }

  public CompletableFuture<Result<Void>> acceptScheduledNoticeEvent(
    ScheduledNoticeConfig noticeConfig, String recipientId, JsonObject context,
    NoticeLogContext noticeLogContext) {

    PatronNotice patronNotice = new PatronNotice();
    patronNotice.setRecipientId(recipientId);
    patronNotice.setTemplateId(noticeConfig.getTemplateId());
    patronNotice.setDeliveryChannel(noticeConfig.getFormat().getDeliveryChannel());
    patronNotice.setOutputFormat(noticeConfig.getFormat().getOutputFormat());
    patronNotice.setContext(context);

    return sendNotice(patronNotice)
      .thenCompose(v -> publishAuditLogEvent(noticeLogContext))
      .exceptionally(t -> succeeded(null));
  }

  public CompletableFuture<Result<Void>> acceptMultipleNoticeEvent(
    Collection<NoticeEventBundle> bundles,
    Function<Collection<JsonObject>, JsonObject> contextCombiner,
    Function<Collection<NoticeLogContext>, NoticeLogContext> logContextCombiner) {

    return allOf(bundles, this::loadNoticePolicyId)
      .thenApply(mapResult(this::groupEvents))
      .thenCompose(r -> r.after(eventGroups -> handleGroupedEvents(eventGroups, contextCombiner, logContextCombiner)));
  }

  private CompletableFuture<Result<Pair<NoticeEventBundle, String>>> loadNoticePolicyId(NoticeEventBundle bundle) {
    return noticePolicyRepository.lookupPolicyId(bundle.getEvent().getItem(), bundle.getEvent().getUser())
      .thenApply(mapResult(circulationRuleMatchEntity -> Pair.of(bundle, circulationRuleMatchEntity.getPolicyId())));
  }

  private Map<NoticeEventGroupDefinition, List<NoticeEventBundle>> groupEvents(
    List<Pair<NoticeEventBundle, String>> eventsWithNoticePolicyId) {

    return eventsWithNoticePolicyId.stream()
      .collect(groupingBy(NoticeEventGroupDefinition::from,
        Collectors.mapping(Pair::getLeft, Collectors.toList())));
  }

  private CompletableFuture<Result<Void>> handleGroupedEvents(
    Map<NoticeEventGroupDefinition, List<NoticeEventBundle>> eventGroups,
    Function<Collection<JsonObject>, JsonObject> contextCombiner,
    Function<Collection<NoticeLogContext>, NoticeLogContext> logContextCombiner) {

    return allOf(eventGroups.entrySet(), e -> handleGroupedEvent(e, contextCombiner, logContextCombiner))
      .thenApply(mapResult(v -> null));
  }

  private CompletableFuture<Result<Void>> handleGroupedEvent(
    Map.Entry<NoticeEventGroupDefinition, List<NoticeEventBundle>> groupedEvent,
    Function<Collection<JsonObject>, JsonObject> contextCombiner,
    Function<Collection<NoticeLogContext>, NoticeLogContext> logContextCombiner) {

    NoticeEventGroupDefinition eventGroupDefinition = groupedEvent.getKey();
    List<NoticeEventBundle> bundles = groupedEvent.getValue();

    JsonObject combinedContext = contextCombiner.apply(bundles.stream()
      .map(NoticeEventBundle::getEvent)
      .map(PatronNoticeEvent::getNoticeContext)
      .collect(Collectors.toList()));

    NoticeLogContext combinedLogContext = logContextCombiner.apply(bundles.stream()
      .map(NoticeEventBundle::getLogContext)
      .collect(Collectors.toList()));
    combinedLogContext.setNoticePolicyId(groupedEvent.getKey().noticePolicyId);

    return noticePolicyRepository.lookupPolicy(
      eventGroupDefinition.noticePolicyId,
      new AppliedRuleConditions(false, false, false))
      .thenCompose(r -> r.after(policy -> applyNoticePolicy(policy, eventGroupDefinition,
        combinedContext, combinedLogContext)));
  }

  private CompletableFuture<Result<Void>> applyNoticePolicy(
    PatronNoticePolicy policy, NoticeEventGroupDefinition eventGroupDefinition,
    JsonObject noticeContext, NoticeLogContext noticeLogContext) {

    Optional<NoticeConfiguration> matchingNoticeConfiguration =
      policy.lookupNoticeConfiguration(eventGroupDefinition.eventType);

    if (!matchingNoticeConfiguration.isPresent()) {
      return completedFuture(succeeded(null));
    }

    matchingNoticeConfiguration.ifPresent(config -> {
      noticeLogContext.setTemplateId(config.getTemplateId());
      noticeLogContext.setTriggeringEvent(config.getNoticeEventType().getRepresentation());
    });

    return sendPatronNotice(matchingNoticeConfiguration.get(),
      eventGroupDefinition.recipientId, noticeContext)
      .thenCompose(r -> r.after(v -> publishAuditLogEvent(noticeLogContext)));
  }

  private CompletableFuture<Result<Void>> sendPatronNotice(
    NoticeConfiguration noticeConfiguration, String recipientId, JsonObject context) {

    PatronNotice patronNotice = new PatronNotice();
    patronNotice.setRecipientId(recipientId);
    patronNotice.setTemplateId(noticeConfiguration.getTemplateId());
    patronNotice.setDeliveryChannel(noticeConfiguration.getNoticeFormat().getDeliveryChannel());
    patronNotice.setOutputFormat(noticeConfiguration.getNoticeFormat().getOutputFormat());
    patronNotice.setContext(context);

    return sendNotice(patronNotice);
  }

  private CompletableFuture<Result<Void>> sendNotice(PatronNotice patronNotice) {
    JsonObject body = JsonObject.mapFrom(patronNotice);
    ResponseInterpreter<Void> responseInterpreter =
      mapToRecordInterpreter(null, 200, 201);

    return patronNoticeClient.post(body)
      .whenComplete((result, error) -> logResponse(result, error, SC_OK, patronNotice))
      .thenApply(responseInterpreter::flatMap);
  }

  private CompletableFuture<Result<Void>> publishAuditLogEvent(NoticeLogContext noticeLogContext) {
    return eventPublisher.publishLogRecord(noticeLogContext.withDate(DateTime.now()).asJson(), NOTICE);
  }

  private static class NoticeEventGroupDefinition {

    private final String recipientId;
    private final String noticePolicyId;
    private final NoticeEventType eventType;

    private static NoticeEventGroupDefinition from(
      Pair<NoticeEventBundle, String> noticeEventWithPolicyId) {

      PatronNoticeEvent event = noticeEventWithPolicyId.getLeft().getEvent();
      String noticePolicyId = noticeEventWithPolicyId.getRight();

      return new NoticeEventGroupDefinition(
        event.getUser().getId(),
        noticePolicyId,
        event.getEventType());
    }

    public NoticeEventGroupDefinition(
      String recipientId, String noticePolicyId,
      NoticeEventType eventType) {

      this.recipientId = recipientId;
      this.noticePolicyId = noticePolicyId;
      this.eventType = eventType;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) return true;

      if (object == null || getClass() != object.getClass()) return false;

      NoticeEventGroupDefinition that = (NoticeEventGroupDefinition) object;

      return new EqualsBuilder()
        .append(recipientId, that.recipientId)
        .append(noticePolicyId, that.noticePolicyId)
        .append(eventType, that.eventType)
        .isEquals();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder(17, 37)
        .append(recipientId)
        .append(noticePolicyId)
        .append(eventType)
        .toHashCode();
    }
  }
}
