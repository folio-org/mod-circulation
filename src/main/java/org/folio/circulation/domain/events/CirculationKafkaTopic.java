package org.folio.circulation.domain.events;

import org.folio.kafka.services.KafkaTopic;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
@AllArgsConstructor
public enum CirculationKafkaTopic implements KafkaTopic {
  ITEM_CHECKED_OUT("ITEM_CHECKED_OUT", 10),
  ITEM_CHECKED_IN("ITEM_CHECKED_IN", 10),
  ITEM_DECLARED_LOST("ITEM_DECLARED_LOST", 10),
  ITEM_AGED_TO_LOST("ITEM_AGED_TO_LOST", 10),
  ITEM_CLAIMED_RETURNED("ITEM_CLAIMED_RETURNED", 10),
  LOAN_DUE_DATE_CHANGED("LOAN_DUE_DATE_CHANGED", 10),
  LOAN_CLOSED("LOAN_CLOSED", 10),
  LOG_RECORD("LOG_RECORD", 10);

  @ToString.Include
  @Getter
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

  public static java.util.Optional<CirculationKafkaTopic> fromEventType(String eventType) {
    for (CirculationKafkaTopic topic : values()) {
      if (topic.topic.equals(eventType)) {
        return java.util.Optional.of(topic);
      }
    }

    return java.util.Optional.empty();
  }
}
