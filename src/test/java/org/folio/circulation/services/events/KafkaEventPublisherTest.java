package org.folio.circulation.services.events;

import static api.support.Wait.waitFor;
import static api.support.Wait.waitForValue;
import static java.lang.System.currentTimeMillis;
import static java.util.UUID.randomUUID;

import java.util.Map;

import org.folio.circulation.domain.events.CirculationKafkaTopic;
import org.folio.circulation.resources.TenantActivationResource;
import org.folio.circulation.support.http.OkapiHeader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import api.support.KafkaTestHelper;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.kafka.client.consumer.KafkaConsumer;

@ExtendWith(VertxExtension.class)
class KafkaEventPublisherTest {

  private static final String TEST_TENANT = "test_tenant";
  private static final Map<String, String> HEADERS = Map.of(
    OkapiHeader.OKAPI_URL, "http://localhost:9130",
    OkapiHeader.TENANT, TEST_TENANT,
    OkapiHeader.TOKEN, "test_token",
    OkapiHeader.USER_ID, randomUUID().toString()
  );

  private static KafkaTestHelper kafkaHelper;

  @BeforeAll
  static void setUp() {
    TenantActivationResource.enableNativeKafkaIntegration();
    kafkaHelper = KafkaTestHelper.getInstance();
  }

  @ParameterizedTest
  @EnumSource(CirculationKafkaTopic.class)
  void testCirculationTopicPublishers(CirculationKafkaTopic topic, Vertx vertx) {
    String fullTopicName = topic.fullTopicName(TEST_TENANT);
    kafkaHelper.createTopic(fullTopicName);

    String consumerGroupId = topic.name() + "-consumer-group-" + randomUUID();
    KafkaConsumer<String, JsonObject> consumer = kafkaHelper.createConsumer(consumerGroupId);
    waitFor(consumer.subscribe(fullTopicName));
    int initialOffset = kafkaHelper.getOffset(fullTopicName, consumerGroupId);

    JsonObject eventPayload = new JsonObject().put("itemId", randomUUID().toString());
    DomainEvent<JsonObject> event = new DomainEvent<>(randomUUID(), DomainEventType.CREATED,
      TEST_TENANT, currentTimeMillis(), eventPayload);

    KafkaEventPublisher<String, JsonObject> publisher =
      new KafkaEventPublisher<>(vertx.getOrCreateContext(), fullTopicName);
    waitFor(publisher.publish(randomUUID().toString(), event, HEADERS));
    waitForValue(() -> kafkaHelper.getOffset(fullTopicName, consumerGroupId), initialOffset + 1);
    consumer.close();
  }
}
