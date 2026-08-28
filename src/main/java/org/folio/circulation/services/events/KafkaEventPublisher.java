package org.folio.circulation.services.events;

import static java.util.Set.of;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.REQUEST_ID;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.circulation.support.http.OkapiHeader.TOKEN;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.kafka.headers.FolioKafkaHeaders.TENANT_ID;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.results.Result;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;

import io.vertx.core.Context;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class KafkaEventPublisher<K> {
  private static final Set<String> FORWARDED_OKAPI_HEADERS = of(
    OKAPI_URL.toLowerCase(),
    TENANT.toLowerCase(),
    TOKEN.toLowerCase(),
    REQUEST_ID.toLowerCase(),
    USER_ID.toLowerCase());

  private final String kafkaTopic;
  private final KafkaProducerManager producerManager;

  public KafkaEventPublisher(Context vertxContext, String kafkaTopic) {
    this(kafkaTopic, createProducerManager(vertxContext));
  }

  public CompletableFuture<Result<Void>> publish(K key, String payload, Map<String, String> okapiHeaders) {
    log.info("publish:: key = {}, topic = {}", key, kafkaTopic);

    KafkaProducerRecord<K, String> producerRecord =
      KafkaProducerRecord.create(kafkaTopic, key, payload);
    producerRecord.addHeader(TENANT_ID, tenantIdFrom(okapiHeaders));
    okapiHeaders.entrySet().stream()
      .filter(entry -> FORWARDED_OKAPI_HEADERS.contains(entry.getKey().toLowerCase()))
      .forEach(entry -> producerRecord.addHeader(entry.getKey(), entry.getValue()));

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

  private static String tenantIdFrom(Map<String, String> okapiHeaders) {
    return okapiHeaders.entrySet().stream()
      .filter(entry -> TENANT.equalsIgnoreCase(entry.getKey()))
      .map(Map.Entry::getValue)
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException(TENANT + " header is required"));
  }

}
