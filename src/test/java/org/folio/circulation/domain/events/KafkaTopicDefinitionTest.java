package org.folio.circulation.domain.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class KafkaTopicDefinitionTest {

  @Test
  void circulationTopicsHaveExpectedConfiguration() {
    Arrays.stream(CirculationKafkaTopic.values())
      .forEach(topic -> assertConfiguration(topic, "circulation", topic.name()));
  }

  @Test
  void circulationStorageTopicHasExpectedConfiguration() {
    KafkaTopicDefinition topic = CirculationStorageKafkaTopic.CIRCULATION_RULES;

    assertConfiguration(topic, "circulation", "rules");
  }

  @Test
  void feeFineTopicsHaveExpectedConfiguration() {
    Arrays.stream(FeeFineKafkaTopic.values())
      .forEach(topic -> assertConfiguration(topic, "feesfines", topic.name()));
  }

  private void assertConfiguration(KafkaTopicDefinition topic, String moduleName,
    String topicName) {

    assertEquals(moduleName, topic.moduleName());
    assertEquals(topicName, topic.topicName());
    assertEquals(KafkaTopicDefinition.DEFAULT_NUM_PARTITIONS, topic.numPartitions());
  }
}
