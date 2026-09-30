package org.folio.circulation.services.events;

import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.results.Result;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class KafkaEventPublisher<K> {
  private final String kafkaTopic;
  private final String tenantId;
  private final KafkaProducerManager producerManager;

  public KafkaEventPublisher(Context vertxContext, String kafkaTopic, String tenantId) {
    this.kafkaTopic = kafkaTopic;
    this.tenantId = tenantId;
    this.producerManager = createProducerManager(vertxContext);
  }

  public CompletableFuture<Result<Void>> publish(K key, JsonObject payload,
    Map<String, String> headers) {

    log.info("publish:: key = {}, topic = {}", key, kafkaTopic);

    KafkaProducerRecord<K, String> producerRecord =
      new KafkaProducerRecordBuilder<K, Object>(tenantId)
        .key(key)
        .value(payload.mapTo(Map.class))
        .topic(kafkaTopic)
        .propagateOkapiHeaders(headers)
        .build();

    KafkaProducer<K, String> producer = null;
    try {
      producer = producerManager.createShared(kafkaTopic);
      producer.exceptionHandler(cause -> log.error(
        "publish:: Kafka producer error for event with key {}", key, cause));
      log.debug("publish:: producer created, sending the record...");

      return producer.send(producerRecord)
        .onSuccess(r -> log.info("publish:: published event with key {}", key))
        .onFailure(cause -> log.error("publish:: failed to publish event with key {}", key, cause))
        .eventually(producer::flush)
        .eventually(producer::close)
        .toCompletionStage()
        .toCompletableFuture()
        .thenApply(ignored -> succeeded(null));
    } catch (Exception e) {
      log.error("publish:: failed to publish event with key {}", key, e);
      if (producer != null) {
        log.debug("publish:: trying to close producer for event {}", key);
        producer.close();
      }
      return failedFuture(e);
    }
  }

  private static KafkaProducerManager createProducerManager(Context vertxContext) {
    var kafkaConfig = KafkaConfig.builder()
        .kafkaPort(KafkaEnvironmentProperties.port())
        .kafkaHost(KafkaEnvironmentProperties.host())
        .build();

    return new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig);
  }

}
