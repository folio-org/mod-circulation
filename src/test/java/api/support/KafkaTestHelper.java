package api.support;

import static api.support.APITestContext.TENANT_ID;
import static api.support.Wait.waitFor;
import static api.support.Wait.waitForSize;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static org.apache.kafka.clients.admin.AlterConfigOp.OpType.DELETE;
import static org.apache.kafka.clients.admin.AlterConfigOp.OpType.SET;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.kafka.KafkaTopicNameHelper.getEventTypeFromTopicName;
import static org.folio.kafka.services.KafkaEnvironmentProperties.environment;
import static org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.circulation.domain.events.CirculationKafkaTopic;
import org.folio.circulation.domain.events.FeeFineKafkaTopic;
import org.folio.circulation.domain.events.CirculationStorageKafkaTopic;
import org.folio.kafka.headers.FolioKafkaHeaders;
import org.folio.kafka.services.KafkaTopic;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.admin.ConsumerGroupDescription;
import io.vertx.kafka.admin.ConsumerGroupListing;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.OffsetAndMetadata;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaTestHelper {

  private static final String MAX_MESSAGE_BYTES = "max.message.bytes";
  private static final DockerImageName KAFKA_IMAGE =
    DockerImageName.parse("apache/kafka-native:4.2.0");
  private static KafkaTestHelper INSTANCE;
  private Vertx vertx;
  private KafkaContainer kafkaContainer;
  private KafkaProducer<String, JsonObject> producer;
  private KafkaAdminClient adminClient;
  private KafkaConsumer<String, String> circulationEventRecorder;
  private String kafkaUrl;

  private KafkaTestHelper() {
    start();
  }

  public static KafkaTestHelper getInstance() {
    if (INSTANCE != null) {
      log.info("getInstance:: returning existing instance");
      return INSTANCE;
    }

    INSTANCE = new KafkaTestHelper();
    return INSTANCE;
  }

  public static KafkaContainer createKafkaContainer() {
    return new KafkaContainer(KAFKA_IMAGE);
  }

  private void start() {
    log.info("start:: starting Kafka test helper");
    setSystemProperties();

    log.info("start:: starting Kafka container");
    KafkaContainer container = createKafkaContainer();
    container.start();
    String host = container.getHost();
    String port = String.valueOf(container.getFirstMappedPort());
    log.info("start:: Kafka container started: host={}, port={}", host, port);

    System.setProperty("kafka-host", host);
    System.setProperty("kafka-port", port);

    this.kafkaContainer = container;
    this.kafkaUrl = String.format("%s:%s", host, port);
    this.vertx = Vertx.vertx();
    this.producer = createProducer();
    this.adminClient = createAdminClient();

    Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
  }

  private void stop() {
    if (kafkaContainer == null || !kafkaContainer.isRunning()) {
      log.info("stop:: Kafka container is not running, nothing to stop");
      return;
    }

    log.info("stop:: stopping Kafka container");
    try {
      kafkaContainer.stop();
    } catch (Exception e) {
      log.error("stop:: failed to stop Kafka container", e);
    }

    if (producer != null) {
      try {
        producer.close();
      } catch (Exception e) {
        log.error("stop:: failed to stop Kafka producer", e);
      }
    }

    if (adminClient != null) {
      try {
        adminClient.close();
      } catch (Exception e) {
        log.error("stop:: failed to stop Kafka admin client", e);
      }
    }
  }

  public void createCirculationTopics(String tenantId) {
    createTopic(CirculationStorageKafkaTopic.CIRCULATION_RULES, tenantId);
  }

  public void createCirculationPublicationTopics(String tenantId) {
    createTopics(Arrays.stream(CirculationKafkaTopic.values())
      .map(topic -> topic.fullTopicName(tenantId))
      .toList());
  }

  public void createFeeFineTopics(String tenantId) {
    createTopics(List.of(
      FeeFineKafkaTopic.FEE_FINE_BALANCE_CHANGED.fullTopicName(tenantId),
      FeeFineKafkaTopic.LOAN_RELATED_FEE_FINE_CLOSED.fullTopicName(tenantId)));
  }

  public Map<String, ConsumerGroupDescription> verifyConsumerGroups(
    Map<String, Integer> groupIdToSize) {

    return waitAtMost(30, SECONDS)
      .until(() -> waitFor(
          adminClient.describeConsumerGroups(new ArrayList<>(groupIdToSize.keySet()))),
        groups -> groups.entrySet()
          .stream()
          .collect(toMap(Map.Entry::getKey, e -> e.getValue().getMembers().size()))
          .entrySet()
          .containsAll(groupIdToSize.entrySet())
      );
  }

  public Collection<String> getConsumerGroups(int expectedGroupCount) {
    return waitAtMost(30, SECONDS)
      .until(() -> waitFor(adminClient.listConsumerGroups()), groups -> groups.size() == expectedGroupCount)
      .stream()
      .map(ConsumerGroupListing::getGroupId)
      .toList();
  }

  public Collection<String> getConsumerGroups() {
    return waitFor(adminClient.listConsumerGroups())
      .stream()
      .map(ConsumerGroupListing::getGroupId)
      .toList();
  }

  public Collection<String> getConsumerGroups(String groupIdPattern) {
    return getConsumerGroups()
      .stream()
      .filter(groupId -> groupId.matches(groupIdPattern))
      .toList();
  }

  public Collection<String> getConsumerGroups(String groupIdPattern, int expectedGroupCount) {
    return waitForSize(() -> getConsumerGroups(groupIdPattern), expectedGroupCount);
  }

  public void deleteConsumerGroup(String groupId) {
    if (groupExists(groupId)) {
      waitFor(adminClient.deleteConsumerGroups(List.of(groupId)));
      if (groupExists(groupId)) {
        fail("Failed to delete consumer group: " + groupId);
      }
    }
  }

  public boolean groupExists(String groupId) {
    return waitFor(adminClient.listConsumerGroups())
      .stream()
      .map(ConsumerGroupListing::getGroupId)
      .anyMatch(groupId::equals);
  }

  public List<String> findConsumerGroupIds(String pattern) {
    return waitFor(adminClient.listConsumerGroups())
      .stream()
      .map(ConsumerGroupListing::getGroupId)
      .filter(groupId -> groupId.matches(pattern))
      .toList();
  }

  @SneakyThrows
  public int getOffset(String topic, String consumerGroupId) {
    Integer offset = waitFor(adminClient.listConsumerGroupOffsets(consumerGroupId)
      .map(partitions -> Optional.ofNullable(partitions.get(new TopicPartition(topic, 0)))
        .map(OffsetAndMetadata::getOffset)
        .map(Long::intValue)
        .orElse(0))); // if topic does not exist yet

    return offset != null ? offset : 0;

  }

  public void publishEvent(String topic, JsonObject eventPayload) {
    publishEvent(topic, eventPayload, Map.of(TENANT, TENANT_ID));
  }

  public void publishEvent(String topic, JsonObject eventPayload, Map<String, String> headers) {
    var kafkaRecord = KafkaProducerRecord.create(topic, UUID.randomUUID().toString(), eventPayload);
    headers.forEach(kafkaRecord::addHeader);
    headers.entrySet().stream()
      .filter(entry -> TENANT.equalsIgnoreCase(entry.getKey()))
      .findFirst()
      .ifPresent(entry -> kafkaRecord.addHeader(FolioKafkaHeaders.TENANT_ID, entry.getValue()));
    waitFor(producer.write(kafkaRecord));
  }

  public AutoCloseable rejectMessagesToTopic(String topic) {
    alterTopicConfig(topic, SET, "1");
    return () -> alterTopicConfig(topic, DELETE, null);
  }

  public void startCirculationEventRecorder(String tenantId) {
    if (circulationEventRecorder != null) {
      return;
    }

    String topicPattern = environment() + "\\." + tenantId + "\\.circulation\\..+";
    circulationEventRecorder = createConsumer("circulation-event-recorder-" + UUID.randomUUID());
    circulationEventRecorder.handler(record -> KafkaPublishedEvents.recordPublishedEvent(
      getEventTypeFromTopicName(record.topic()), record.value()));
    waitFor(circulationEventRecorder.subscribe(Pattern.compile(topicPattern)));
  }

  public KafkaProducer<String, JsonObject> createProducer() {
    Properties config = new Properties();
    config.put(BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
    config.put(ACKS_CONFIG, "1");

    return KafkaProducer.create(vertx, config, String.class, JsonObject.class);
  }

  public KafkaConsumer<String, String> createConsumer(String consumerGroupId) {
    Properties config = new Properties();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
    config.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
      StringDeserializer.class.getName());
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
      StringDeserializer.class.getName());
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

    return KafkaConsumer.create(vertx, config);
  }

  public KafkaAdminClient createAdminClient() {
    Properties config = new Properties();
    config.put(BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);

    return KafkaAdminClient.create(vertx, config);
  }

  public <K, V> Collection<ConsumerRecord<K,V>> consumeEvents(KafkaConsumer<K, V> consumer,
    String topic, int expectedEventCount) {

    Collection<ConsumerRecord<K, V>> allRecords = new ArrayList<>();
    long deadline = currentTimeMillis() + Duration.ofSeconds(30).toMillis();
    while (allRecords.size() < expectedEventCount && currentTimeMillis() < deadline) {
      waitFor(consumer.poll(Duration.ofSeconds(5)))
        .records()
        .records(topic)
        .iterator()
        .forEachRemaining(allRecords::add);
    }
    waitFor(consumer.commit());
    return allRecords;
  }

  public void createTopic(KafkaTopic topic, String tenantId) {
    createTopic(topic.fullTopicName(tenantId));
  }

  public void createTopic(String topic) {
    createTopics(List.of(topic));
  }

  public void createTopics(Collection<String> topics) {
    Set<String> existingTopics = listTopics();
    List<String> nonExistentTopics = topics.stream()
      .filter(not(existingTopics::contains))
      .toList();

    if (nonExistentTopics.isEmpty()) {
      return;
    }

    List<NewTopic> newTopics = nonExistentTopics.stream()
      .map(topic -> new NewTopic(topic, 1, (short) 1))
      .toList();

    waitFor(adminClient.createTopics(newTopics));
    verifyTopicsExist(topics);
  }

  public Set<String> listTopics() {
    return waitFor(adminClient.listTopics());
  }

  public void deleteAllTopics() {
    deleteTopics(listTopics());
  }

  public void deleteTopics(Collection<String> topics) {
    List<String> existingTopics = listTopics()
      .stream()
      .filter(topics::contains)
      .toList();

    waitFor(adminClient.deleteTopics(existingTopics));
    verifyTopicsDoNotExist(topics);
  }

  public void clearTopic(String topic) {
    clearTopics(List.of(topic));
  }

  public void clearAllTopics() {
    clearTopics(listTopics());
  }

  public void clearTopics(Collection<String> topics) {
    if (topics.isEmpty()) {
      return;
    }

    List<String> existingTopics = listTopics()
      .stream()
      .filter(topics::contains)
      .toList();

    deleteTopics(existingTopics);
    createTopics(existingTopics);
  }

  public void verifyTopicExists(String topic) {
    verifyTopicsExist(List.of(topic));
  }

  public void verifyTopicsExist(Collection<String> topics) {
    waitFor(() -> listTopics().containsAll(topics));
  }

  public void verifyTopicDoesNotExist(String topic) {
    verifyTopicsDoNotExist(List.of(topic));
  }

  public void verifyTopicsDoNotExist(Collection<String> topics) {
    waitFor(() -> listTopics().stream().noneMatch(topics::contains));
  }


  public void waitForTopicCount(int expectedCount) {
    waitForSize(this::listTopics, expectedCount);
  }

  @SneakyThrows
  private void alterTopicConfig(String topic, AlterConfigOp.OpType operation, String value) {
    Properties config = new Properties();
    config.put(BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);

    var resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
    var configEntry = new ConfigEntry(MAX_MESSAGE_BYTES, value);
    var configOperation = new AlterConfigOp(configEntry, operation);

    try (Admin admin = Admin.create(config)) {
      admin.incrementalAlterConfigs(Map.of(resource, List.of(configOperation)))
        .all()
        .get(30, SECONDS);
    }
  }

  private static void setSystemProperties() {
    // Set Kafka consumer to read messages from the beginning of the topic if no offset is present.
    // Helps avoid race condition between consumer and producer in tests.
    System.setProperty("kafka.consumer.auto.offset.reset", "earliest");
  }
}
