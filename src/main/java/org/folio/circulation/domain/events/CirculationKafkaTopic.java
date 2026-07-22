package org.folio.circulation.domain.events;

import org.folio.kafka.services.KafkaTopic;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum CirculationKafkaTopic implements KafkaTopic {
  ITEM_CHECKED_OUT("ITEM_CHECKED_OUT", 10),
  ITEM_CHECKED_IN("ITEM_CHECKED_IN", 10),
  ITEM_DECLARED_LOST("ITEM_DECLARED_LOST", 10),
  ITEM_AGED_TO_LOST("ITEM_AGED_TO_LOST", 10),
  ITEM_CLAIMED_RETURNED("ITEM_CLAIMED_RETURNED", 10),
  LOAN_DUE_DATE_CHANGED("LOAN_DUE_DATE_CHANGED", 10),
  LOAN_CLOSED("LOAN_CLOSED", 10);

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

