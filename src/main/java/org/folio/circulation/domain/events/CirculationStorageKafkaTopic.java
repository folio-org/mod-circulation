package org.folio.circulation.domain.events;

import org.folio.kafka.services.KafkaTopic;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum CirculationStorageKafkaTopic implements KafkaTopic {
  CIRCULATION_RULES("rules", 10);

  private final String topic;
  private final int partitions;

  @Override
  public String moduleName() {
    return "circulation";
  }

  @Override
  public String topicName() {
    return topic;
  }

  @Override
  public int numPartitions() {
    return partitions;
  }
}

