package org.folio.circulation.services.events;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.domain.events.DomainEventMapper.toEntityChangedEvent;
import static org.folio.circulation.domain.events.DomainEventPayloadType.UPDATED;

import org.folio.circulation.domain.events.DomainEvent;
import org.folio.circulation.domain.events.EntityChangedEventData;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.kafka.AsyncRecordHandler;

import io.vertx.core.Future;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CirculationRulesUpdateEventHandler implements AsyncRecordHandler<String, String> {

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> consumerRecord) {
    final String eventKey = consumerRecord.key();

    final DomainEvent<EntityChangedEventData> event;
    try {
      final String eventValue = consumerRecord.value();
      log.info("handle:: event received: key={}", eventKey);
      log.debug("handle:: value={}", eventValue);

      event = toEntityChangedEvent(eventValue);
      if (event.payloadType() != UPDATED) {
        log.warn("handle:: unsupported event type: {}", event.payloadType());
        return succeededFuture(eventKey);
      }

      validate(event);
    } catch (Exception e) {
      log.warn("handle:: invalid circulation rules update event skipped: key={}, reason={}",
        eventKey, invalidEventReason(e));
      return succeededFuture(eventKey);
    }

    try {
      CirculationRulesCache.getInstance().handleRulesUpdateEvent(event);
      log.info("handle:: circulation rules update event processed: {}", eventKey);
      return succeededFuture(eventKey);
    } catch (Exception e) {
      log.error("handle:: failed to apply circulation rules update event", e);
      return failedFuture(e);
    }
  }

  private static void validate(DomainEvent<EntityChangedEventData> event) {
    log.info("validate:: validating event: {}", event.id());
    if (isBlank(event.data().newVersion().getString("rulesAsText"))) {
      throw new IllegalArgumentException("Event does not contain new circulation rules: " + event);
    }
    log.debug("validate:: event validation complete: {}", event.id());
  }

  private static String invalidEventReason(Exception exception) {
    if (exception instanceof NullPointerException || isBlank(exception.getMessage())) {
      return "missing required event field";
    }

    return exception.getMessage();
  }

}
