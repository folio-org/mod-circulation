package org.folio.circulation;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.getenv;
import static org.folio.Environment.getHttpMaxPoolSize;
import static org.folio.circulation.domain.EventType.FEE_FINE_BALANCE_CHANGED;
import static org.folio.circulation.domain.EventType.LOAN_RELATED_FEE_FINE_CLOSED;
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
import java.util.Properties;

import org.folio.circulation.domain.EventType;
import org.folio.circulation.domain.events.FeeFineKafkaTopic;
import org.folio.circulation.domain.events.DomainEventType;
import org.folio.circulation.services.events.CirculationRulesUpdateEventHandler;
import org.folio.circulation.services.events.FeeFineBalanceChangedKafkaEventHandler;
import org.folio.circulation.services.events.LoanRelatedFeeFineClosedKafkaEventHandler;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaTopic;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class EventConsumerVerticle extends AbstractVerticle {

  public static final String MODULE_NAME = "mod-circulation";
  public static final String REAL_MODULE_ID = String.format("%s-%s",
    MODULE_NAME, moduleVersion());
  private static final String MODULE_VERSION_RESOURCE =
    "/META-INF/maven/org.folio/mod-circulation/pom.properties";
  private static final int DEFAULT_LOAD_LIMIT = 5;
  private static final String TENANT_ID_PATTERN = "\\w+";
  private static final String DEFAULT_OKAPI_URL = "http://okapi:9130";
  private static final int DEFAULT_KAFKA_MAX_REQUEST_SIZE = 4000000;
  private  static final String AUTO_OFFSET_RESET_PROPERTY = "kafka.consumer.auto.offset.reset";
  private  static final String AUTO_OFFSET_RESET_LATEST = "latest";

  private final List<KafkaConsumerWrapper<String, String>> consumers = new ArrayList<>();
  private KafkaConfig kafkaConfig;
  private HttpClient httpClient;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    kafkaConfig = buildKafkaConfig();
    httpClient = vertx.createHttpClient(new PoolOptions()
      .setHttp1MaxSize(getHttpMaxPoolSize())
      .setHttp2MaxSize(getHttpMaxPoolSize()));
    setSystemProperties();
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
      .compose(v -> httpClient.close())
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
        buildUniqueModuleId()), // puts consumers into separate groups so that they all receive the same event
      createConsumer(LOAN_RELATED_FEE_FINE_CLOSED, FeeFineKafkaTopic.LOAN_RELATED_FEE_FINE_CLOSED,
        new LoanRelatedFeeFineClosedKafkaEventHandler(httpClient, kafkaConfig.getOkapiUrl()),
        REAL_MODULE_ID),
      createConsumer(FEE_FINE_BALANCE_CHANGED, FeeFineKafkaTopic.FEE_FINE_BALANCE_CHANGED,
        new FeeFineBalanceChangedKafkaEventHandler(httpClient, kafkaConfig.getOkapiUrl()),
        REAL_MODULE_ID)
    )).mapEmpty();
  }

  private Future<KafkaConsumerWrapper<String, String>> createConsumer(DomainEventType eventType,
    AsyncRecordHandler<String, String> handler, String moduleId) {

    log.info("createConsumer:: creating consumer for event type {}", eventType);

    return createConsumer(eventType.name(), eventType.getKafkaTopic(), handler, moduleId);
  }

  private Future<KafkaConsumerWrapper<String, String>> createConsumer(EventType eventType,
    KafkaTopic kafkaTopic, AsyncRecordHandler<String, String> handler, String moduleId) {

    log.info("createConsumer:: creating consumer for event type {}", eventType);

    return createConsumer(eventType.name(), kafkaTopic, handler, moduleId);
  }

  private Future<KafkaConsumerWrapper<String, String>> createConsumer(String eventType,
    KafkaTopic kafkaTopic, AsyncRecordHandler<String, String> handler, String moduleId) {

    KafkaConsumerWrapper<String, String> consumer = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(DEFAULT_LOAD_LIMIT)
      .globalLoadSensor(new GlobalLoadSensor())
      .subscriptionDefinition(buildSubscriptionDefinition(eventType, kafkaTopic))
      .processRecordErrorHandler((t, r) -> log.error("Failed to process event: {}", r, t))
      .build();

    return consumer.start(handler, moduleId)
      .map(consumer)
      .onSuccess(consumers::add);
  }

  private static SubscriptionDefinition buildSubscriptionDefinition(String eventType,
    KafkaTopic kafkaTopic) {

    return SubscriptionDefinition.builder()
      .eventType(eventType)
      .subscriptionPattern(kafkaTopic.fullTopicName(TENANT_ID_PATTERN))
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

  public static String consumerGroupId(EventType eventType) {
    return org.folio.kafka.KafkaTopicNameHelper.formatGroupName(eventType.name(), REAL_MODULE_ID);
  }

  private static void setSystemProperties() {
    // This is for CIRCULATION_RULES_UPDATED topic consumers only.
    // Consider removing this when adding another consumer.
    String autoOffsetReset = System.getProperty(AUTO_OFFSET_RESET_PROPERTY);
    if (autoOffsetReset == null) {
      log.info("setSystemProperties:: setting system property: {}={}",
        AUTO_OFFSET_RESET_PROPERTY, AUTO_OFFSET_RESET_LATEST);
      System.setProperty(AUTO_OFFSET_RESET_PROPERTY, AUTO_OFFSET_RESET_LATEST);
    } else {
      log.info("setSystemProperties:: system property {} is already set to '{}', doing nothing",
        AUTO_OFFSET_RESET_PROPERTY, autoOffsetReset);
    }
  }

  private static String moduleVersion() {
    try (var stream = EventConsumerVerticle.class.getResourceAsStream(MODULE_VERSION_RESOURCE)) {
      if (stream == null) {
        return "24.6.0";
      }

      var properties = new Properties();
      properties.load(stream);
      return properties.getProperty("version", "24.6.0").replace("-SNAPSHOT", "");
    } catch (Exception e) {
      log.warn("moduleVersion:: failed to read module version, using fallback", e);
      return "24.6.0";
    }
  }

}
