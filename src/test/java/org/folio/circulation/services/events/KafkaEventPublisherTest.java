package org.folio.circulation.services.events;

import static api.support.Wait.waitFor;
import static api.support.Wait.waitForValue;
import static java.lang.System.currentTimeMillis;
import static java.util.UUID.randomUUID;
import static org.folio.circulation.services.events.KafkaEventPublisherFactory.itemCheckedInEventPublisher;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.folio.circulation.domain.events.CirculationKafkaTopic;
import org.folio.circulation.resources.TenantActivationResource;
import org.folio.circulation.support.http.OkapiHeader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import api.support.KafkaTestHelper;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

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
    kafkaHelper = KafkaTestHelper.start();
  }

  @Test
  void itemCheckedInEventPublisherTest(Vertx vertx) {
    String topic = CirculationKafkaTopic.ITEM_CHECKED_IN.fullTopicName(TEST_TENANT);
    kafkaHelper.createTopic(topic);

    String consumerGroupId = "test-group";
    var consumer = kafkaHelper.createConsumer(consumerGroupId);
    waitFor(consumer.subscribe(topic));
    int initialOffset = kafkaHelper.getOffset(topic, consumerGroupId);
    assertEquals(0, initialOffset);

    var eventPayload = new JsonObject().put("itemId", randomUUID().toString());
    var event = new DomainEvent<>(randomUUID(), DomainEventType.CREATED,
      TEST_TENANT, currentTimeMillis(), eventPayload);

    var publisher = itemCheckedInEventPublisher(vertx.getOrCreateContext(), HEADERS);
    waitFor(publisher.publish(randomUUID().toString(), event, HEADERS));
    waitForValue(() -> kafkaHelper.getOffset(topic, consumerGroupId), 1);
  }
}
