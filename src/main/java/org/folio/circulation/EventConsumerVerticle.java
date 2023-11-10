package org.folio.circulation;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.System.getenv;
import static java.util.Comparator.comparing;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.folio.circulation.domain.events.kafka.KafkaEventType.CIRCULATION_RULES_UPDATED;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.KAFKA_ENV;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.KAFKA_HOST;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.KAFKA_MAX_REQUEST_SIZE;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.KAFKA_PORT;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.KAFKA_REPLICATION_FACTOR;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.OKAPI_URL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.folio.circulation.domain.events.kafka.KafkaEventType;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.services.events.CirculationRulesUpdateEventHandler;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.util.pubsub.support.PomReader;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.admin.ConsumerGroupDescription;
import io.vertx.kafka.admin.ConsumerGroupListing;
import io.vertx.kafka.admin.KafkaAdminClient;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class EventConsumerVerticle extends AbstractVerticle {

  private static final int DEFAULT_LOAD_LIMIT = 5;
  private static final String TENANT_ID_PATTERN = "\\w+";
  private static final String DEFAULT_OKAPI_URL = "http://okapi:9130";
  private static final int DEFAULT_KAFKA_MAX_REQUEST_SIZE = 4000000;
  private static final String MODULE_ID = buildModuleId();

  private final List<KafkaConsumerWrapper<String, String>> consumers = new ArrayList<>();
  private KafkaConfig kafkaConfig;
  private KafkaAdminClient kafkaAdminClient;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    kafkaConfig = buildKafkaConfig();
    kafkaAdminClient = createKafkaAdminClient();

    vertx.setPeriodic(10000, r -> log.info("Cached rules: " + CirculationRulesCache.getInstance().getRules("diku")));
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
    return Future.all(
      consumers.stream()
        .map(KafkaConsumerWrapper::stop)
        .toList())
      .onSuccess(v -> log.info("stop:: event consumers stopped"))
      .onFailure(t -> log.error("stop:: failed to stop event consumers", t))
      .mapEmpty();
  }

  private Future<Void> createConsumers() {
    return Future.all(List.of(
      createConsumer(CIRCULATION_RULES_UPDATED, new CirculationRulesUpdateEventHandler(), true)
    )).mapEmpty();
  }

  private Future<KafkaConsumerWrapper<String, String>> createConsumer(KafkaEventType eventType,
    AsyncRecordHandler<String, String> handler, boolean useUniqueGroupId) {

    KafkaConsumerWrapper<String, String> consumer = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(DEFAULT_LOAD_LIMIT)
      .globalLoadSensor(new GlobalLoadSensor())
      .subscriptionDefinition(buildSubscriptionDefinition(eventType))
      .build();

//    return consumer.start(handler, MODULE_ID)
//      .map(consumer)
//      .onSuccess(consumers::add);

    return getModuleId(eventType, useUniqueGroupId)
      .compose(moduleId -> consumer.start(handler, moduleId))
      .map(consumer)
      .onSuccess(consumers::add);
  }

  private static SubscriptionDefinition buildSubscriptionDefinition(KafkaEventType eventType) {
    return SubscriptionDefinition.builder()
      .eventType(eventType.name())
      .subscriptionPattern(eventType.getKafkaTopic().fullTopicName(TENANT_ID_PATTERN))
      .build();
  }

  private KafkaConfig buildKafkaConfig() {
    log.info("getKafkaConfig:: building Kafka config");

//    JsonObject vertxConfig = vertx.getOrCreateContext().config();
    final JsonObject vertxConfig = config();

    KafkaConfig config = KafkaConfig.builder()
      .envId(vertxConfig.getString(KAFKA_ENV))
      .kafkaHost(vertxConfig.getString(KAFKA_HOST))
      .kafkaPort(vertxConfig.getString(KAFKA_PORT))
      .okapiUrl(vertxConfig.getString(OKAPI_URL))
      .replicationFactor(Integer.parseInt(vertxConfig.getString(KAFKA_REPLICATION_FACTOR, "1")))
      .maxRequestSize(Integer.parseInt(vertxConfig.getString(KAFKA_MAX_REQUEST_SIZE, "10")))
      .build();

    log.info("getKafkaConfig:: {}", config);

    return config;
  }

  private KafkaAdminClient createKafkaAdminClient() {
    Properties config = new Properties();
    config.put(BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getKafkaUrl());
    return KafkaAdminClient.create(vertx, config);
  }

//  private Future<Void> listGroups() {
//    log.info("listing consumer groups");
//    return createKafkaAdminClient()
//      .listConsumerGroups()
//      .onSuccess(list -> list.stream().map(ConsumerGroupListing::getGroupId).forEach(groupId -> log.info("Consumer group found: {}", groupId)))
//      .mapEmpty();
//  }

  public static String buildModuleId() {
    return PomReader.INSTANCE.getModuleName() + "-" + PomReader.INSTANCE.getVersion();
  }

  private Future<String> getModuleId(KafkaEventType eventType, boolean unique) {
    return unique ? getUniqueModuleId(eventType) : succeededFuture(buildModuleId());
  }

  private Future<String> getUniqueModuleId(KafkaEventType eventType) {
    return kafkaAdminClient.listConsumerGroups()
      .map(groups -> extractGroupIds(eventType, groups))
      .compose(kafkaAdminClient::describeConsumerGroups)
      .map(groups -> getUniqueModuleId(eventType, groups));
  }

  private String getUniqueModuleId(KafkaEventType eventType,
    Map<String, ConsumerGroupDescription> groups) {

    List<ConsumerGroupDescription> sortedGroups = groups.values()
      .stream()
      .sorted(comparing(ConsumerGroupDescription::getGroupId))
      .toList();

    String consumerGroupId = groups.values()
      .stream()
      .sorted(comparing(ConsumerGroupDescription::getGroupId))
      .filter(group -> group.getMembers().isEmpty())
      .findFirst()
      .map(ConsumerGroupDescription::getGroupId)
      .orElseGet(() -> buildNewConsumerGroupId(sortedGroups, eventType));

    return consumerGroupId.substring(consumerGroupId.lastIndexOf(buildModuleId()));
  }

  private String buildNewConsumerGroupId(List<ConsumerGroupDescription> existingGroups,
    KafkaEventType eventType) {

    List<String> existingGroupIds = existingGroups.stream()
      .map(ConsumerGroupDescription::getGroupId)
      .toList();

    for (int i = 0; i < Integer.MAX_VALUE; i++) {
      String moduleId = buildModuleId() + "-subgroup-" + i;
      String possibleGroupId = KafkaTopicNameHelper.formatGroupName(eventType.name(), moduleId);
      if (!existingGroupIds.contains(possibleGroupId)) {
        return possibleGroupId;
      }
    }

    throw new RuntimeException("could not find unique consumer group id");
  }

  private static List<String> extractGroupIds(KafkaEventType eventType,
    List<ConsumerGroupListing> groups) {

    return groups.stream()
      .map(ConsumerGroupListing::getGroupId)
      .filter(id -> id.startsWith(KafkaTopicNameHelper.formatGroupName(eventType.name(), buildModuleId()) + "-subgroup-"))
      .sorted()
      .toList();
  }

  public static JsonObject buildConfig() {
    return new JsonObject()
      .put(KAFKA_HOST, KafkaEnvironmentProperties.host())
      .put(KAFKA_PORT, KafkaEnvironmentProperties.port())
      .put(KAFKA_REPLICATION_FACTOR, KafkaEnvironmentProperties.replicationFactor())
      .put(KAFKA_ENV, KafkaEnvironmentProperties.environment())
      .put(OKAPI_URL, getenv().getOrDefault(OKAPI_URL, DEFAULT_OKAPI_URL))
      .put(KAFKA_MAX_REQUEST_SIZE, getenv().getOrDefault(KAFKA_MAX_REQUEST_SIZE,
        String.valueOf(DEFAULT_KAFKA_MAX_REQUEST_SIZE)));
  }

}
