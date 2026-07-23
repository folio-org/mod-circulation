package org.folio.circulation.domain.events;

import org.folio.kafka.services.KafkaTopic;

import lombok.AllArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public enum CirculationStorageKafkaTopic implements KafkaTopic {
  CIRCULATION_RULES("rules", 10);

  @ToString.Include
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

