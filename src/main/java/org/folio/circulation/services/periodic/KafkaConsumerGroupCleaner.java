package org.folio.circulation.services.periodic;

import static org.folio.circulation.domain.events.DomainEventType.CIRCULATION_RULES_UPDATED;
import static org.folio.kafka.KafkaTopicNameHelper.formatGroupName;

import java.util.List;
import java.util.Map;

import org.folio.circulation.domain.events.DomainEventType;
import org.folio.circulation.services.events.ModuleIdProvider;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.rest.resource.interfaces.PeriodicAPI;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.kafka.admin.ConsumerGroupDescription;
import io.vertx.kafka.admin.ConsumerGroupListing;
import io.vertx.kafka.admin.KafkaAdminClient;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaConsumerGroupCleaner implements PeriodicAPI {
  private final static DomainEventType CIRCULATION_RULES_UPDATED_EVENT_TYPE
    = CIRCULATION_RULES_UPDATED;
  private final static int EXPIRATION_PERIOD_MILLIS = 60 * 60 * 1000; // 1 hour

  @Override
  public long runEvery() {
    return 60;
  }

  @Override
  public void run(Vertx vertx, Context context) {
    try {
      Map<String, String> config = KafkaConfig.builder()
        .kafkaHost(KafkaEnvironmentProperties.host())
        .kafkaPort(KafkaEnvironmentProperties.port())
        .build()
        .getProducerProps();

      log.info("KafkaConsumerGroupCleaner.run:: KafkaAdminClient config: {}", config);

      KafkaAdminClient kafkaAdminClient = KafkaAdminClient.create(vertx, config);

      kafkaAdminClient.listConsumerGroups()
        .compose(consumerGroupListing -> kafkaAdminClient.listConsumerGroups())
        .map(this::filterExpiredConsumerGroups)
        .compose(kafkaAdminClient::describeConsumerGroups)
        .map(this::filterEmptyConsumerGroups)
        .map(this::logDeletionAttempt)
        .compose(kafkaAdminClient::deleteConsumerGroups)
        .onSuccess(r -> log.info("Successfully deleted expired consumer groups"))
        .onFailure(t -> log.error("Failed to delete expired consumer groups", t))
        .onComplete(r -> kafkaAdminClient.close());
    } catch (Exception e) {
      log.error("KafkaConsumerGroupCleaner.run:: failed to run Periodic API", e);
    }
  }

  private List<String> filterExpiredConsumerGroups(List<ConsumerGroupListing> consumerGroups) {
    return consumerGroups.stream()
      .map(ConsumerGroupListing::getGroupId)
      .filter(groupId -> groupId.startsWith(formatGroupName(
        CIRCULATION_RULES_UPDATED_EVENT_TYPE.name(), ModuleIdProvider.REAL_MODULE_ID)))
      .filter(this::isConsumerGroupExpired)
      .toList();
  }

  private List<String> filterEmptyConsumerGroups(Map<String, ConsumerGroupDescription> consumerGroups) {
    return consumerGroups.values().stream()
      .filter(description -> description.getMembers().isEmpty())
      .map(ConsumerGroupDescription::getGroupId)
      .toList();
  }

  private List<String> logDeletionAttempt(List<String> consumerGroups) {
    log.info("deleteConsumerGroups:: Deleting consumer groups: {}", consumerGroups);
    return consumerGroups;
  }

  private boolean isConsumerGroupExpired(String consumerGroupName) {
    log.debug("isConsumerGroupExpired:: Consumer group name: {}", consumerGroupName);
    long timestamp = Long.parseLong(extractTimeMillisFromModuleId(consumerGroupName));
    long oneHourAgo = System.currentTimeMillis() - EXPIRATION_PERIOD_MILLIS;
    return timestamp > 0 && timestamp < oneHourAgo;
  }

  private String extractTimeMillisFromModuleId(String consumerGroupName) {
    log.debug("extractTimeMillisFromModuleId:: Consumer group name: {}", consumerGroupName);
    String[] parts = consumerGroupName.split("_");
    return parts[parts.length - 1];
  }
}
