package org.folio.circulation.services.events;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.circulation.domain.events.kafka.DomainEventMapper.toEntityChangedEvent;

import java.util.Optional;

import org.folio.circulation.domain.events.kafka.DomainEvent;
import org.folio.circulation.domain.events.kafka.EntityChangedEventData;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.rules.cache.Rules;
import org.folio.circulation.support.results.Result;
import org.folio.kafka.AsyncRecordHandler;

import io.vertx.core.Future;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CirculationRulesUpdateEventHandler implements AsyncRecordHandler<String, String> {

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> consumerRecord) {
    final String eventKey = consumerRecord.key();
    final String eventValue = consumerRecord.value();

    log.info("handle:: event received: key={}", eventKey);
    log.debug("handle:: payload={}", eventValue);

    DomainEvent<EntityChangedEventData> event = toEntityChangedEvent(eventValue);
    String newRulesAsText = Optional.ofNullable(event.data())
      .map(EntityChangedEventData::newVersion)
      .map(newRules -> newRules.getString("rulesAsText"))
      .orElse(EMPTY);

    Result<Rules> result = CirculationRulesCache.getInstance()
      .reloadRules(event.tenantId(), newRulesAsText);

    if (result.succeeded()) {
      log.info("handle:: circulation rules update event processed");
      return succeededFuture(eventKey);
    } else {
      log.error("handle:: failed to process circulation rules update event: {}", result.cause());
      return failedFuture(result.cause().toString());
    }
  }

}
