package org.folio.circulation.services.events;

import static java.util.Comparator.comparing;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.folio.kafka.KafkaTopicNameHelper.formatGroupName;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.folio.circulation.domain.events.DomainEventType;
import org.folio.kafka.KafkaConfig;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.kafka.admin.ConsumerGroupDescription;
import io.vertx.kafka.admin.ConsumerGroupListing;
import io.vertx.kafka.admin.KafkaAdminClient;

public class UniqueKafkaModuleIdProvider implements ModuleIdProvider {
  private final KafkaAdminClient kafkaAdminClient;
  private final DomainEventType eventType;

  public UniqueKafkaModuleIdProvider(Vertx vertx, KafkaConfig kafkaConfig, DomainEventType eventType) {
    Properties config = new Properties();
    config.put(BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getKafkaUrl());

    this.kafkaAdminClient = KafkaAdminClient.create(vertx, config);
    this.eventType = eventType;
  }

  @Override
  public Future<String> getModuleId() {
    return kafkaAdminClient.listConsumerGroups()
      .map(this::extractConsumerGroupIds)
      .compose(kafkaAdminClient::describeConsumerGroups)
      .map(this::getUniqueModuleId);
  }

  private List<String> extractConsumerGroupIds(List<ConsumerGroupListing> groups) {
    return groups.stream()
      .map(ConsumerGroupListing::getGroupId)
      .filter(id -> id.startsWith(formatGroupName(eventType.name(), REAL_MODULE_ID)))
      .sorted()
      .toList();
  }

  private String getUniqueModuleId(Map<String, ConsumerGroupDescription> groups) {
    List<ConsumerGroupDescription> sortedGroups = groups.values()
      .stream()
      .sorted(comparing(ConsumerGroupDescription::getGroupId))
      .toList();

    return sortedGroups.stream()
      .filter(group -> group.getMembers().isEmpty())
      .findFirst()
      .map(ConsumerGroupDescription::getGroupId)
      .map(emptyGroupId -> emptyGroupId.substring(emptyGroupId.lastIndexOf(REAL_MODULE_ID)))
      .orElseGet(() -> findUniqueModuleId(sortedGroups));
  }

  private String findUniqueModuleId(List<ConsumerGroupDescription> existingGroups) {
    List<String> existingGroupIds = existingGroups.stream()
      .map(ConsumerGroupDescription::getGroupId)
      .toList();

    // in case list of group IDs has gaps
    for (int i = 0; i < existingGroupIds.size(); i++) {
      String candidateModuleId = buildUniqueModuleId(i);
      String candidateGroupId = formatGroupName(eventType.name(), candidateModuleId);
      if (!existingGroupIds.get(i).equals(candidateGroupId)) {
        return candidateModuleId;
      }
    }
    // list of group IDs has no gaps
    return buildUniqueModuleId(existingGroups.size());
  }

  private static String buildUniqueModuleId(int subgroupOrdinal) {
    return String.format("%s-subgroup-%d", REAL_MODULE_ID, subgroupOrdinal);
  }

}
