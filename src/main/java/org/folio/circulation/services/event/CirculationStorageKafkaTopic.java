package org.folio.circulation.services.event;

import org.folio.kafka.services.KafkaTopic;

public enum CirculationStorageKafkaTopic implements KafkaTopic {
  CIRCULATION_RULES("circulation-rules", 10);

  private final String topic;
  private final int partitions;

  CirculationStorageKafkaTopic(String topic, int partitions) {
    this.topic = topic;
    this.partitions = partitions;
  }

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
