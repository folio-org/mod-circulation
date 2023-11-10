package org.folio.circulation.services.events;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.rules.cache.Rules;
import org.folio.circulation.support.results.Result;
import org.folio.kafka.AsyncRecordHandler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CirculationRulesUpdateEventHandler implements AsyncRecordHandler<String, String> {

  //TODO deserialize payload into POJO

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> event) {
    log.info("handle:: event received: key={}", event.key());
    log.debug("handle:: payload={}", event::value);

    JsonObject payload = new JsonObject(event.value());
    JsonObject data = payload.getJsonObject("data");
    Result<Rules> result = CirculationRulesCache.getInstance()
      .reloadRules(payload.getString("tenant"),
        data.getJsonObject("new").getString("rulesAsText"));

    if (result.succeeded()) {
      log.info("handle:: circulation rules update event processed");
      return succeededFuture(event.key());
    } else {
      log.error("handle:: failed to process circulation rules update event: {}", result.cause());
      return failedFuture(result.cause().toString());
    }
  }
}
