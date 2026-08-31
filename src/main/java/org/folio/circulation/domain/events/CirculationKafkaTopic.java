package org.folio.circulation.domain.events;

import java.util.Optional;

public enum CirculationKafkaTopic implements KafkaTopicDefinition {
  ITEM_CHECKED_OUT,
  ITEM_CHECKED_IN,
  ITEM_DECLARED_LOST,
  ITEM_AGED_TO_LOST,
  ITEM_CLAIMED_RETURNED,
  LOAN_DUE_DATE_CHANGED,
  LOAN_CLOSED,
  LOG_RECORD;

  @Override
  public String moduleName() {
    return "circulation";
  }

  public static Optional<CirculationKafkaTopic> fromEventType(String eventType) {
    for (CirculationKafkaTopic topic : values()) {
      if (topic.topicName().equals(eventType)) {
        return Optional.of(topic);
      }
    }

    return Optional.empty();
  }
}
