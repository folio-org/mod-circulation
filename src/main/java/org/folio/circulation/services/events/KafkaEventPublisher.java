package org.folio.circulation.services.events;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.results.Result;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Context;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class KafkaEventPublisher<K, T> {

  private final String kafkaTopic;
  private final KafkaProducerManager producerManager;

  public KafkaEventPublisher(Context vertxContext, String kafkaTopic) {
    this(kafkaTopic, createProducerManager(vertxContext));
  }

  public CompletableFuture<Result<Void>> publish(K key, DomainEvent<T> event, Map<String, String> okapiHeaders) {
    log.info("publish:: key = {}, eventId = {}, type = {}, topic = {}", key, event.getId(),
      event.getType(), kafkaTopic);

    KafkaProducerRecord<K, String> producerRecord =
      new KafkaProducerRecordBuilder<K, DomainEvent<T>>(TenantTool.tenantId(okapiHeaders))
        .key(key)
        .value(event)
        .topic(kafkaTopic)
        .propagateOkapiHeaders(okapiHeaders)
        .build();

    KafkaProducer<K, String> producer = null;
    try {
      producer = getOrCreateProducer();
      log.info("publish:: Producer created, sending the record...");

      producer.send(producerRecord)
        .onSuccess(r -> log.info("publish:: Succeeded sending domain event with key [{}]", key))
        .onFailure(cause -> log.error("publish:: Unable to send domain event with key [{}]", key, cause))
        .eventually(producer::flush)
        .eventually(producer::close);
    } catch (Exception e) {
      log.error("publish:: Failed to initiate send for domain event with key [{}]", key, e);
      if (producer != null) {
        log.info("publish:: Producer is not null, trying to close. Event key: {}.", key);
        producer.close();
      }
    }

    return Result.emptyAsync();
  }

  private KafkaProducer<K, String> getOrCreateProducer() {
    return getOrCreateProducer("");
  }

  private KafkaProducer<K, String> getOrCreateProducer(String prefix) {
    return producerManager.createShared(prefix + kafkaTopic);
  }

  private static KafkaProducerManager createProducerManager(Context vertxContext) {
    var kafkaConfig = KafkaConfig.builder()
        .kafkaPort(KafkaEnvironmentProperties.port())
        .kafkaHost(KafkaEnvironmentProperties.host())
        .build();

    return new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig);
  }

}
