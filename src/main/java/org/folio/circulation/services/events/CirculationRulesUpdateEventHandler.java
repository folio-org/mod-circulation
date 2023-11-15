package org.folio.circulation.services.events;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.circulation.domain.events.DomainEventMapper.toEntityChangedEvent;

import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.kafka.AsyncRecordHandler;

import io.vertx.core.Future;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CirculationRulesUpdateEventHandler implements AsyncRecordHandler<String, String> {

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> consumerRecord) {
    try {
      final String eventKey = consumerRecord.key();
      final String eventValue = consumerRecord.value();

      log.info("handle:: event received: key={}", eventKey);
      log.debug("handle:: value={}", eventValue);

      CirculationRulesCache.getInstance()
        .handleRulesUpdateEvent(toEntityChangedEvent(eventValue));

      log.info("handle:: circulation rules update event processed: {}", eventKey);
      return succeededFuture(eventKey);
    } catch (Exception e) {
      log.error("handle:: failed to process circulation rules update event", e);
      return failedFuture(e);
    }
  }

}
