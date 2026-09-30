package org.folio.circulation.services.events;

import static api.support.Wait.waitFor;
import static api.support.Wait.waitForValue;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.circulation.domain.events.AuditKafkaTopic;
import org.folio.circulation.domain.events.CirculationKafkaTopic;
import org.folio.circulation.domain.events.KafkaTopicDefinition;
import org.folio.circulation.support.http.OkapiHeader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import api.support.KafkaTestHelper;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

class KafkaEventPublisherTest {

  private static final String TEST_TENANT = "test";
  private static final Map<String, String> HEADERS = Map.of(
    OkapiHeader.OKAPI_URL, "http://localhost:9130",
    OkapiHeader.TENANT, TEST_TENANT,
    OkapiHeader.TOKEN, "test_token",
    OkapiHeader.USER_ID, randomUUID().toString()
  );

  private static KafkaTestHelper kafkaHelper;

  @BeforeAll
  static void setUp() {
    kafkaHelper = KafkaTestHelper.getInstance();
  }

  @ParameterizedTest
  @MethodSource("publicationTopics")
  void testTopicPublishers(KafkaTopicDefinition topic) {
    String fullTopicName = topic.fullTopicName(TEST_TENANT);
    kafkaHelper.createTopic(fullTopicName);
    kafkaHelper.verifyTopicExists(fullTopicName);

    String consumerGroupId = topic.name() + "-consumer-group-" + randomUUID();
    var consumer = kafkaHelper.createConsumer(consumerGroupId);
    waitFor(consumer.subscribe(fullTopicName));
    int initialOffset = kafkaHelper.getOffset(fullTopicName, consumerGroupId);

    JsonObject eventPayload = new JsonObject().put("itemId", randomUUID().toString());

    KafkaEventPublisher<String> publisher =
      new KafkaEventPublisher<>(Vertx.vertx().getOrCreateContext(), fullTopicName, TEST_TENANT);
    waitFor(publisher.publish(randomUUID().toString(), eventPayload, HEADERS));
    Collection<ConsumerRecord<String, String>> consumerRecords =
      kafkaHelper.consumeEvents(consumer, fullTopicName, 1);

    assertEquals(1, consumerRecords.size());
    assertEquals(eventPayload.encode(), consumerRecords.iterator().next().value());
    waitForValue(() -> kafkaHelper.getOffset(fullTopicName, consumerGroupId), initialOffset + 1);
    consumer.close();
  }

  private static Stream<KafkaTopicDefinition> publicationTopics() {
    return Stream.<KafkaTopicDefinition>concat(
      Arrays.stream(CirculationKafkaTopic.values()),
      Arrays.stream(AuditKafkaTopic.values()));
  }
}
