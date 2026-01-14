package org.folio.circulation;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.getenv;
import static org.folio.circulation.domain.events.DomainEventType.CIRCULATION_RULES_UPDATED;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.KAFKA_ENV;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.KAFKA_HOST;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.KAFKA_MAX_REQUEST_SIZE;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.KAFKA_PORT;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.KAFKA_REPLICATION_FACTOR;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.OKAPI_URL;
import static org.folio.circulation.support.utils.RandomUtil.generateRandomDigits;

import java.util.ArrayList;
import java.util.List;

import org.folio.circulation.domain.events.DomainEventType;
import org.folio.circulation.services.events.CirculationRulesUpdateEventHandler;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.util.pubsub.support.PomReader;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class EventConsumerVerticle extends AbstractVerticle {

  public static final String REAL_MODULE_ID = String.format("%s-%s",
    PomReader.INSTANCE.getModuleName(), PomReader.INSTANCE.getVersion());
  private static final int DEFAULT_LOAD_LIMIT = 5;
  private static final String TENANT_ID_PATTERN = "\\w+";
  private static final String DEFAULT_OKAPI_URL = "http://okapi:9130";
  private static final int DEFAULT_KAFKA_MAX_REQUEST_SIZE = 4000000;

  private final List<KafkaConsumerWrapper<String, String>> consumers = new ArrayList<>();
  private KafkaConfig kafkaConfig;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    kafkaConfig = buildKafkaConfig();
    // This is for CIRCULATION_RULES_UPDATED topic consumers only.
    // Consider removing this line when adding another consumer.
//    System.setProperty("kafka.consumer.auto.offset.reset", "latest");
  }

  @Override
  public void start(Promise<Void> promise) {
    log.info("start:: starting verticle");

    createConsumers()
      .onSuccess(v -> log.info("start:: verticle started"))
      .onFailure(t -> log.error("start:: verticle start failed", t))
      .onComplete(promise);
  }

  @Override
  public void stop(Promise<Void> promise) {
    log.info("stop:: stopping verticle");

    stopConsumers()
      .onSuccess(v -> log.info("stop:: verticle stopped"))
      .onFailure(t -> log.error("stop:: verticle stop failed", t))
      .onComplete(promise);
  }

  private Future<Void> stopConsumers() {
    log.info("stopConsumers:: stopping consumers");
    return Future.all(
      consumers.stream()
        .map(KafkaConsumerWrapper::stop)
        .toList())
      .onSuccess(v -> log.info("stopConsumers:: event consumers stopped"))
      .onFailure(t -> log.error("stopConsumers:: failed to stop event consumers", t))
      .mapEmpty();
  }

  private Future<Void> createConsumers() {
    log.info("createConsumers:: creating consumers");
    return Future.all(List.of(
      createConsumer(CIRCULATION_RULES_UPDATED, new CirculationRulesUpdateEventHandler(),
        buildUniqueModuleId()) // puts consumers into separate groups so that they all receive the same event
    )).mapEmpty();
  }

  private Future<KafkaConsumerWrapper<String, String>> createConsumer(DomainEventType eventType,
    AsyncRecordHandler<String, String> handler, String moduleId) {

    log.info("createConsumer:: creating consumer for event type {}", eventType);

    KafkaConsumerWrapper<String, String> consumer = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(DEFAULT_LOAD_LIMIT)
      .globalLoadSensor(new GlobalLoadSensor())
      .subscriptionDefinition(buildSubscriptionDefinition(eventType))
      .processRecordErrorHandler((t, r) -> log.error("Failed to process event: {}", r, t))
      .build();

    return consumer.start(handler, moduleId)
      .map(consumer)
      .onSuccess(consumers::add);
  }

  private static SubscriptionDefinition buildSubscriptionDefinition(DomainEventType eventType) {
    return SubscriptionDefinition.builder()
      .eventType(eventType.name())
      .subscriptionPattern(eventType.getKafkaTopic().fullTopicName(TENANT_ID_PATTERN))
      .build();
  }

  private KafkaConfig buildKafkaConfig() {
    log.info("buildKafkaConfig:: building Kafka config");
    final JsonObject vertxConfig = config();

    KafkaConfig config = KafkaConfig.builder()
      .envId(vertxConfig.getString(KAFKA_ENV))
      .kafkaHost(vertxConfig.getString(KAFKA_HOST))
      .kafkaPort(vertxConfig.getString(KAFKA_PORT))
      .okapiUrl(vertxConfig.getString(OKAPI_URL))
      .replicationFactor(Integer.parseInt(vertxConfig.getString(KAFKA_REPLICATION_FACTOR, "1")))
      .maxRequestSize(Integer.parseInt(vertxConfig.getString(KAFKA_MAX_REQUEST_SIZE, "10")))
      .build();

    log.debug("buildKafkaConfig:: {}", config);
    return config;
  }

  public static JsonObject buildConfig() {
    log.info("buildConfig:: building config for {}", EventConsumerVerticle.class.getSimpleName());
    return new JsonObject()
      .put(KAFKA_HOST, KafkaEnvironmentProperties.host())
      .put(KAFKA_PORT, KafkaEnvironmentProperties.port())
      .put(KAFKA_REPLICATION_FACTOR, KafkaEnvironmentProperties.replicationFactor())
      .put(KAFKA_ENV, KafkaEnvironmentProperties.environment())
      .put(OKAPI_URL, getenv().getOrDefault(OKAPI_URL, DEFAULT_OKAPI_URL))
      .put(KAFKA_MAX_REQUEST_SIZE, getenv().getOrDefault(KAFKA_MAX_REQUEST_SIZE,
        String.valueOf(DEFAULT_KAFKA_MAX_REQUEST_SIZE)));
  }

  public String buildUniqueModuleId() {
      String id = String.format("%s_%s_%s", REAL_MODULE_ID, generateRandomDigits(10), currentTimeMillis());
      log.info("buildUniqueModuleId:: using module ID {}", id);
      return id;
  }

}
