package org.folio.circulation.services.events;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.folio.kafka.KafkaTopicNameHelper.formatGroupName;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.folio.circulation.domain.events.DomainEventType;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.services.KafkaAdminClientService;
import org.folio.kafka.services.KafkaEnvironmentProperties;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.kafka.admin.ConsumerGroupDescription;
import io.vertx.kafka.admin.ConsumerGroupListing;
import io.vertx.kafka.admin.KafkaAdminClient;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class UniqueKafkaModuleIdProvider implements ModuleIdProvider {
  private final KafkaAdminClient kafkaAdminClient;
  private final DomainEventType eventType;

  public UniqueKafkaModuleIdProvider(Vertx vertx, KafkaConfig kafkaConfig, DomainEventType eventType) {
//    Properties config = new Properties();
//    config.put(BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getKafkaUrl());
//    log.info("UniqueKafkaModuleIdProvider:: KafkaAdminClient config={}", config);

//    this.kafkaAdminClient = KafkaAdminClient.create(vertx, config);
    var config = KafkaConfig.builder()
//      .kafkaHost(kafkaConfig.getKafkaHost())
//      .kafkaPort(kafkaConfig.getKafkaPort())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build()
      .getProducerProps();

    log.info("UniqueKafkaModuleIdProvider:: KafkaAdminClient config={}", config);

    this.kafkaAdminClient = KafkaAdminClient.create(vertx, config);
    this.eventType = eventType;
  }

  @Override
  public Future<String> getModuleId() {
    log.info("getModuleId:: getting unique module ID: eventType={}", eventType);

//    return succeededFuture(REAL_MODULE_ID);
    return kafkaAdminClient.listConsumerGroups()
      .map(this::extractConsumerGroupIds)
      .compose(kafkaAdminClient::describeConsumerGroups)
      .map(this::getUniqueModuleId);
  }

  private List<String> extractConsumerGroupIds(List<ConsumerGroupListing> groups) {
    log.debug("extractConsumerGroupIds:: groups={}", groups);

    List<String> existingGroupIds = groups.stream()
      .map(ConsumerGroupListing::getGroupId)
      .filter(id -> id.startsWith(formatGroupName(eventType.name(), REAL_MODULE_ID)))
      .sorted()
      .toList();

    log.info("extractConsumerGroupIds:: existing consumer groups: {}", existingGroupIds);
    return existingGroupIds;
  }

  private String getUniqueModuleId(Map<String, ConsumerGroupDescription> groups) {
    log.debug("getUniqueModuleId:: groups={}", groups);

    List<ConsumerGroupDescription> sortedGroups = groups.values()
      .stream()
      .sorted(comparing(ConsumerGroupDescription::getGroupId))
      .toList();

    log.info("getUniqueModuleId:: group sizes: {}", sortedGroups.stream()
      .collect(toMap(ConsumerGroupDescription::getGroupId, group -> group.getMembers().size())));

    return sortedGroups.stream()
      .filter(group -> group.getMembers().isEmpty())
      .findFirst()
      .map(ConsumerGroupDescription::getGroupId)
      .map(peek(emptyGroupId -> log.info("getUniqueModuleId:: empty group found: {}", emptyGroupId)))
      .map(emptyGroupId -> emptyGroupId.substring(emptyGroupId.lastIndexOf(REAL_MODULE_ID)))
      .orElseGet(() -> findUniqueModuleId(sortedGroups));
  }

  private String findUniqueModuleId(List<ConsumerGroupDescription> groups) {
    log.debug("findUniqueModuleId:: groups={}", groups);

    List<String> existingGroupIds = groups.stream()
      .map(ConsumerGroupDescription::getGroupId)
      .toList();

    log.info("findUniqueModuleId:: looking for gaps in list of group IDs: {}", existingGroupIds);

    for (int i = 0; i < existingGroupIds.size(); i++) {
      String candidateModuleId = buildUniqueModuleId(i);
      String candidateGroupId = formatGroupName(eventType.name(), candidateModuleId);
      log.debug("findUniqueModuleId:: checking group ID: {}", candidateGroupId);
      if (!existingGroupIds.get(i).equals(candidateGroupId)) {
        log.info("findUniqueModuleId:: found a gap in list of group IDs, using module ID {}",
          candidateModuleId);
        return candidateModuleId;
      }
    }

    final String newModuleId = buildUniqueModuleId(groups.size());
    log.info("findUniqueModuleId:: found no gaps in list of group IDs, using module ID {}",
      newModuleId);

    return newModuleId;
  }

  private static String buildUniqueModuleId(int subgroupOrdinal) {
    return String.format("%s-subgroup-%d", REAL_MODULE_ID, subgroupOrdinal);
  }

  private static <T> UnaryOperator<T> peek(Consumer<T> consumer) {
    return x -> {
      consumer.accept(x);
      return x;
    };
  }

}
