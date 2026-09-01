package org.folio.kafka;

import static java.util.Map.of;
import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KafkaConfigWithConsumerOverridesTest {

  @Test
  void overridesConsumerProperties() {
    KafkaConfig baseConfig = KafkaConfig.builder()
      .kafkaHost("localhost")
      .kafkaPort("9092")
      .build();

    KafkaConfig config = new KafkaConfigWithConsumerOverrides(baseConfig, of(
      AUTO_OFFSET_RESET_CONFIG, "latest",
      MAX_POLL_RECORDS_CONFIG, "25"));

    assertEquals("latest", config.getConsumerProps().get(AUTO_OFFSET_RESET_CONFIG));
    assertEquals("25", config.getConsumerProps().get(MAX_POLL_RECORDS_CONFIG));
  }
}
