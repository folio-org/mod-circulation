package api.support.utl;

import static api.support.APITestContext.TENANT_ID;
import static api.support.Wait.waitFor;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.events.DomainEventType.CIRCULATION_RULES_UPDATED;
import static org.folio.kafka.services.KafkaEnvironmentProperties.environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import org.folio.circulation.domain.events.DomainEventType;
import org.folio.circulation.support.VertxAssistant;
import org.folio.util.pubsub.support.PomReader;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.admin.ConsumerGroupDescription;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.OffsetAndMetadata;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaHelper {
  private static final String CIRCULATION_RULES_TOPIC = format("%s.%s.circulation.rules",
    environment(), TENANT_ID);
  private static final KafkaContainer kafkaContainer =
    new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.2.0"));

  private static KafkaHelper instance;

  private final String kafkaUrl;
  private final VertxAssistant vertxAssistant;
  private final KafkaProducer<String, JsonObject> kafkaProducer;
  private final KafkaAdminClient kafkaAdminClient;

  public static KafkaHelper getInstance() {
    if (instance == null) {
      instance = new KafkaHelper();
    }
    return instance;
  }

  private KafkaHelper() {
    Runtime.getRuntime().addShutdownHook(new Thread(kafkaContainer::stop));

    if (kafkaContainer.isRunning()) {
      log.info("runKafka:: Kafka container is already running");
    } else {
      log.info("runKafka:: starting Kafka container...");
      kafkaContainer.start();
    }

    String host = kafkaContainer.getHost();
    String port = String.valueOf(kafkaContainer.getFirstMappedPort());
    log.info("runKafka:: Kafka container is running: host={}, port={}", host, port);

    System.setProperty("kafka-host", host);
    System.setProperty("kafka-port", port);

    this.kafkaUrl = format("%s:%s", host, port);
    this.vertxAssistant = createVertxAssistant();
    this.kafkaProducer = createKafkaProducer();
    this.kafkaAdminClient = createKafkaAdminClient();
  }

  @SneakyThrows
  public int getOffset(String topic, String consumerGroupId) {
    return waitFor(kafkaAdminClient.listConsumerGroupOffsets(consumerGroupId)
      .map(partitions ->  Optional.ofNullable(partitions.get(new TopicPartition(topic, 0)))
        .map(OffsetAndMetadata::getOffset)
        .map(Long::intValue)
        .orElse(0) // if topic does not exist yet
      ));
  }

  public Map<String, ConsumerGroupDescription> verifyConsumerGroups(
    Map<String, Integer> groupIdToSize) {

    return waitAtMost(30, SECONDS)
      .until(() -> waitFor(kafkaAdminClient.describeConsumerGroups(new ArrayList<>(groupIdToSize.keySet()))),
        groups -> groups.entrySet()
          .stream()
          .collect(toMap(Map.Entry::getKey, e -> e.getValue().getMembers().size()))
          .equals(groupIdToSize)
      );
  }

  public int getOffsetForCirculationRulesUpdateEvents() {
    return getOffsetForCirculationRulesUpdateEvents(0);
  }

  public int getOffsetForCirculationRulesUpdateEvents(int subgroupOrdinal) {
    return getOffset(CIRCULATION_RULES_TOPIC,
      buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, subgroupOrdinal));
  }

  public String buildConsumerSubgroupId(DomainEventType eventType, int subgroupOrdinal) {
    return String.format("%s-subgroup-%d", buildConsumerGroupId(eventType), subgroupOrdinal);
  }

  private String buildConsumerGroupId(DomainEventType eventType) {
    return format("%s.%s-%s", eventType, PomReader.INSTANCE.getModuleName(), PomReader.INSTANCE.getVersion());
  }

  public Future<RecordMetadata> publishCirculationRulesUpdateEvent(JsonObject oldVersion,
    JsonObject newVersion) {

    return publishEvent(CIRCULATION_RULES_TOPIC, buildUpdateEvent(oldVersion, newVersion));
  }

  public Future<RecordMetadata> publishUpdateEvent(String topic, JsonObject oldVersion,
    JsonObject newVersion) {

    return publishEvent(topic, buildUpdateEvent(oldVersion, newVersion));
  }

  public Future<RecordMetadata> publishEvent(String topic, JsonObject event) {
    return kafkaProducer.send(KafkaProducerRecord.create(topic, randomId(), event));
  }

  public JsonObject buildUpdateEvent(JsonObject oldVersion, JsonObject newVersion) {
    return new JsonObject()
      .put("id", randomId())
      .put("tenant", TENANT_ID)
      .put("type", "UPDATED")
      .put("timestamp", System.currentTimeMillis())
      .put("data", new JsonObject()
        .put("old", oldVersion)
        .put("new", newVersion));
  }

  public Future<Void> deleteConsumerGroup(String consumerGroupId) {
    return deleteConsumerGroups(List.of(consumerGroupId));
  }

  public Future<Void> deleteConsumerGroups(List<String> consumerGroupIds) {
    return kafkaAdminClient.deleteConsumerGroups(consumerGroupIds);
  }

  public String buildTopicName(String module, String topic) {
    return format("%s.%s.%s.%s", environment(), TENANT_ID, module, topic);
  }

  private String randomId() {
    return UUID.randomUUID().toString();
  }

  private KafkaProducer<String, JsonObject> createKafkaProducer() {
    Properties config = new Properties();
    config.put(BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
    config.put(ACKS_CONFIG, "1");

    return vertxAssistant.createUsingVertx(vertx ->
      KafkaProducer.create(vertx, config, String.class, JsonObject.class));
  }

  private KafkaAdminClient createKafkaAdminClient() {
    Properties config = new Properties();
    config.put(BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);

    return vertxAssistant.createUsingVertx(vertx -> KafkaAdminClient.create(vertx, config));
  }

  private static VertxAssistant createVertxAssistant() {
    VertxAssistant vertxAssistant = new VertxAssistant();
    vertxAssistant.start();

    return vertxAssistant;
  }



}
