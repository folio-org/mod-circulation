package org.folio.circulation.services.periodic;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.System.currentTimeMillis;
import static org.folio.circulation.domain.events.DomainEventType.CIRCULATION_RULES_UPDATED;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.folio.kafka.KafkaConfig;
import org.folio.kafka.services.KafkaEnvironmentProperties;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.kafka.admin.ConsumerGroupDescription;
import io.vertx.kafka.admin.ConsumerGroupListing;
import io.vertx.kafka.admin.KafkaAdminClient;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaConsumerGroupCleaner {

  private final static int CLEANUP_INITIAL_DELAY_MILLIS = 5000;
  private final static int CLEANUP_PERIOD_MILLIS = 60 * 60 * 1000;
  private final static int EMPTY_GROUP_AGE_THRESHOLD_MILLIS = 24 * 60 * 60 * 1000;
  private static final String CIRCULATION_RULES_UPDATED_CONSUMER_GROUP_NAME_PATTERN =
    CIRCULATION_RULES_UPDATED.name() + "\\.mod-circulation-[\\d.]+_\\d+_\\d+";

  private final Vertx vertx;
  private final KafkaAdminClient adminClient;

  public KafkaConsumerGroupCleaner(Vertx vertx) {
    this.vertx = vertx;
    this.adminClient = KafkaAdminClient.create(vertx, buildKafkaConfig());
  }

  public void start() {
    log.info("start:: scheduling expired consumer group cleanup");
    vertx.setPeriodic(CLEANUP_INITIAL_DELAY_MILLIS, CLEANUP_PERIOD_MILLIS, timerId -> runCleanup());
  }

  public void stop() {
    log.info("disable:: stopping expired consumer group cleanup");
    adminClient.close();
  }

  public void runCleanup() {
    log.info("runCleanup:: running expired empty consumer group cleanup");

    adminClient.listConsumerGroups()
      .map(this::filterExpiredConsumerGroups)
      .compose(this::describeConsumerGroups)
      .compose(this::deleteEmptyConsumerGroups)
      .onSuccess(r -> log.info("runCleanup:: consumer group cleanup done"))
      .onFailure(t -> log.error("runCleanup:: consumer group cleanup failed", t));
  }

  private List<String> filterExpiredConsumerGroups(List<ConsumerGroupListing> consumerGroups) {
    List<String> expiredGroups = consumerGroups.stream()
      .map(ConsumerGroupListing::getGroupId)
      .filter(this::isConsumerGroupExpired)
      .toList();

    log.info("filterExpiredConsumerGroups:: found {} expired consumer groups", expiredGroups::size);
    return expiredGroups;
  }

  private Future<Map<String, ConsumerGroupDescription>> describeConsumerGroups(
    List<String> consumerGroupIds) {

    if (consumerGroupIds.isEmpty()) {
      log.info("describeConsumerGroups:: no consumer groups to describe");
      return succeededFuture(new HashMap<>());
    }

    return adminClient.describeConsumerGroups(consumerGroupIds);
  }

  private Future<Void> deleteEmptyConsumerGroups(Map<String, ConsumerGroupDescription> consumerGroups) {
    List<String> emptyGroupIds = consumerGroups.values().stream()
      .filter(description -> description.getMembers().isEmpty())
      .map(ConsumerGroupDescription::getGroupId)
      .toList();

    if (emptyGroupIds.isEmpty()) {
      log.info("deleteEmptyConsumerGroups:: no empty consumer groups to delete");
      return succeededFuture();
    }

    log.info("deleteEmptyConsumerGroups:: deleting {} consumer groups: {}", emptyGroupIds.size(),  emptyGroupIds);
    return adminClient.deleteConsumerGroups(emptyGroupIds);
  }

  private boolean isConsumerGroupExpired(String consumerGroupName) {
    if (!consumerGroupName.matches(CIRCULATION_RULES_UPDATED_CONSUMER_GROUP_NAME_PATTERN)) {
      log.info("isConsumerGroupExpired:: skipping group: {}", consumerGroupName);
      return false;
    }

    log.info("isConsumerGroupExpired:: Consumer group name: {}", consumerGroupName);
    long timestamp = extractTimestamp(consumerGroupName);
    return timestamp > 0 && (currentTimeMillis() - timestamp) > EMPTY_GROUP_AGE_THRESHOLD_MILLIS;
  }

  private static long extractTimestamp(String groupName) {
    log.info("extractTimestamp:: Consumer group name: {}", groupName);
    long timestamp;
    try {
      timestamp = Long.parseLong(groupName.substring(groupName.lastIndexOf("_") + 1));
      log.info("extractTimestamp:: timestamp: {}", timestamp);
    } catch (NumberFormatException e) {
      log.error("extractTimestamp:: failed to extract timestamp from group name: {}", groupName, e);
      timestamp = -1;
    }
    return timestamp;
  }

  private static Map<String, String> buildKafkaConfig() {
    Map<String, String> config = KafkaConfig.builder()
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build()
      .getProducerProps();

    log.info("buildKafkaConfig:: config: {}", config);
    return config;
  }
}