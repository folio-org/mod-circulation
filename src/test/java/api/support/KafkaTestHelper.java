package api.support;

import static api.support.APITestContext.TENANT_ID;
import static api.support.APITestContext.createKafkaAdminClient;
import static api.support.APITestContext.createKafkaConsumer;
import static api.support.APITestContext.createKafkaProducer;
import static api.support.Wait.waitFor;
import static api.support.Wait.waitForSize;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

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

  private static KafkaTestHelper INSTANCE;
  private final KafkaProducer<String, JsonObject> producer;
  private final KafkaAdminClient adminClient;
  private final String kafkaUrl;

  private KafkaTestHelper() {
    setSystemProperties();
    KafkaContainer container = initContainer();
    String host = container.getHost();
    String port = String.valueOf(container.getFirstMappedPort());
    log.info("Kafka container started: host={}, port={}", host, port);

    System.setProperty("kafka-host", host);
    System.setProperty("kafka-port", port);

    this.kafkaUrl = String.format("%s:%s", host, port);
    this.producer = createKafkaProducer(kafkaUrl);
    this.adminClient = createKafkaAdminClient(kafkaUrl);
  }

  public static KafkaTestHelper start() {
    if (INSTANCE != null) {
      log.info("Kafka container is already running");
      return INSTANCE;
    }
    INSTANCE = new KafkaTestHelper();
    return INSTANCE;
  }

  private static KafkaContainer initContainer() {
    log.info("starting Kafka container...");
    KafkaContainer container = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.2.0"));
    container.start();
    Runtime.getRuntime().addShutdownHook(new Thread(container::stop));

    return container;
  }

  public Map<String, ConsumerGroupDescription> verifyConsumerGroups(
    Map<String, Integer> groupIdToSize) {

    return waitAtMost(30, SECONDS)
      .until(() -> waitFor(
          adminClient.describeConsumerGroups(new ArrayList<>(groupIdToSize.keySet()))),
        groups -> groups.entrySet()
          .stream()
          .collect(toMap(Map.Entry::getKey, e -> e.getValue().getMembers().size()))
          .equals(groupIdToSize)
      );
  }

  public List<String> getConsumerGroups(int expectedGroupCount) {
    return waitAtMost(30, SECONDS)
      .until(() -> waitFor(adminClient.listConsumerGroups()), groups -> groups.size() == expectedGroupCount)
      .stream()
      .map(ConsumerGroupListing::getGroupId)
      .toList();
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
    return waitFor(adminClient.listConsumerGroupOffsets(consumerGroupId)
      .map(partitions -> Optional.ofNullable(partitions.get(new TopicPartition(topic, 0)))
        .map(OffsetAndMetadata::getOffset)
        .map(Long::intValue)
        .orElse(0))); // if topic does not exist yet
  }

  public void publishEvent(String topic, JsonObject eventPayload) {
    var record = KafkaProducerRecord.create(topic, UUID.randomUUID().toString(), eventPayload);
    record.addHeader("X-Okapi-Tenant", TENANT_ID);
    waitFor(producer.write(record));
  }

  public void createTopic(String topic) {
    createTopics(List.of(topic));
  }

  public void createTopics(List<String> topics) {
    List<NewTopic> newTopics = topics.stream()
      .map(topic -> new NewTopic(topic, 1, (short) 1))
      .toList();

    waitFor(adminClient.createTopics(newTopics));
  }

  public Set<String> listTopics() {
    return waitFor(adminClient.listTopics());
  }

  public KafkaConsumer<String, JsonObject> createConsumer(String consumerGroupId) {
    return createKafkaConsumer(kafkaUrl, consumerGroupId);
  }

  public void deleteAllTopics() {
    waitFor(adminClient.listTopics()
      .compose(topics -> adminClient.deleteTopics(new ArrayList<>(topics))));
  }

  public void waitForTopicCount(int expectedCount) {
    waitForSize(this::listTopics, expectedCount);
  }

  private static void setSystemProperties() {
    // Set Kafka consumer to read messages from the beginning of the topic if no offset is present.
    // Helps avoid race condition between consumer and producer in tests.
    System.setProperty("kafka.consumer.auto.offset.reset", "earliest");
  }
}
