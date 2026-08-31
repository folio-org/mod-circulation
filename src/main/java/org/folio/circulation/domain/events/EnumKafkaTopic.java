package org.folio.circulation.domain.events;

import org.folio.kafka.services.KafkaTopic;

public interface EnumKafkaTopic extends KafkaTopic {
  int DEFAULT_NUM_PARTITIONS = 10;

  String name();

  @Override
  default String topicName() {
    return name();
  }

  @Override
  default int numPartitions() {
    return DEFAULT_NUM_PARTITIONS;
  }
}
