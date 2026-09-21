package org.folio.circulation.services.events;

import static java.util.concurrent.CompletableFuture.failedFuture;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.results.Result;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;

import io.vertx.core.Context;
import io.vertx.core.Future;
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
    long publishingStartedAt = System.nanoTime();

    KafkaProducerRecord<K, String> producerRecord =
      new KafkaProducerRecordBuilder<K, Object>(tenantId)
        .key(key)
        .value(payload.mapTo(Map.class))
        .topic(kafkaTopic)
        .propagateOkapiHeaders(headers)
        .build();

    KafkaProducer<K, String> producer = null;
    try {
      log.debug("publish:: creating producer: key={}, topic={}, tenantId={}, thread={}",
        key, kafkaTopic, tenantId, Thread.currentThread().getName());
      producer = producerManager.createShared(kafkaTopic);
      KafkaProducer<K, String> sharedProducer = producer;
      log.debug("publish:: producer created: key={}, topic={}, elapsedMs={}, thread={}",
        key, kafkaTopic, elapsedMillis(publishingStartedAt), Thread.currentThread().getName());
      producer.exceptionHandler(cause -> log.error(
        "publish:: Kafka producer error for event with key {}", key, cause));
      log.debug("publish:: sending record: key={}, topic={}, thread={}",
        key, kafkaTopic, Thread.currentThread().getName());

      return producer.send(producerRecord)
        .onSuccess(r -> log.info(
          "publish:: send completed: key={}, topic={}, elapsedMs={}, thread={}",
          key, kafkaTopic, elapsedMillis(publishingStartedAt), Thread.currentThread().getName()))
        .onFailure(cause -> log.error(
          "publish:: send failed: key={}, topic={}, elapsedMs={}, thread={}",
          key, kafkaTopic, elapsedMillis(publishingStartedAt), Thread.currentThread().getName(), cause))
        .eventually(() -> flush(sharedProducer, key))
        .eventually(() -> close(sharedProducer, key))
        .toCompletionStage()
        .toCompletableFuture()
        .thenApply(ignored -> Result.<Void>succeeded(null))
        .whenComplete((result, cause) -> {
          if (cause == null) {
            log.debug("publish:: completed: key={}, topic={}, elapsedMs={}, thread={}",
              key, kafkaTopic, elapsedMillis(publishingStartedAt),
              Thread.currentThread().getName());
          } else {
            log.error("publish:: failed: key={}, topic={}, elapsedMs={}, thread={}",
              key, kafkaTopic, elapsedMillis(publishingStartedAt),
              Thread.currentThread().getName(), cause);
          }
        });
    } catch (Exception e) {
      log.error("publish:: failed to publish event with key {}", key, e);
      if (producer != null) {
        log.debug("publish:: trying to close producer for event {}", key);
        producer.close();
      }
      return failedFuture(e);
    }
  }

  private Future<Void> flush(KafkaProducer<K, String> producer, K key) {
    long startedAt = System.nanoTime();
    log.debug("publish:: flushing producer: key={}, topic={}, thread={}",
      key, kafkaTopic, Thread.currentThread().getName());

    return producer.flush()
      .onSuccess(ignored -> log.debug(
        "publish:: producer flushed: key={}, topic={}, elapsedMs={}, thread={}",
        key, kafkaTopic, elapsedMillis(startedAt), Thread.currentThread().getName()))
      .onFailure(cause -> log.error(
        "publish:: producer flush failed: key={}, topic={}, elapsedMs={}, thread={}",
        key, kafkaTopic, elapsedMillis(startedAt), Thread.currentThread().getName(), cause));
  }

  private Future<Void> close(KafkaProducer<K, String> producer, K key) {
    long startedAt = System.nanoTime();
    log.debug("publish:: closing producer: key={}, topic={}, thread={}",
      key, kafkaTopic, Thread.currentThread().getName());

    return producer.close()
      .onSuccess(ignored -> log.debug(
        "publish:: producer closed: key={}, topic={}, elapsedMs={}, thread={}",
        key, kafkaTopic, elapsedMillis(startedAt), Thread.currentThread().getName()))
      .onFailure(cause -> log.error(
        "publish:: producer close failed: key={}, topic={}, elapsedMs={}, thread={}",
        key, kafkaTopic, elapsedMillis(startedAt), Thread.currentThread().getName(), cause));
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }

  private static KafkaProducerManager createProducerManager(Context vertxContext) {
    var kafkaConfig = KafkaConfig.builder()
        .kafkaPort(KafkaEnvironmentProperties.port())
        .kafkaHost(KafkaEnvironmentProperties.host())
        .build();

    return new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig);
  }

}
