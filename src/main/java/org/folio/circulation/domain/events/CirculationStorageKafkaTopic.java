package org.folio.circulation.domain.events;

public enum CirculationStorageKafkaTopic implements KafkaTopicDefinition {
  CIRCULATION_RULES;

  @Override
  public String moduleName() {
    return "circulation";
  }

  @Override
  public String topicName() {
    return "rules";
  }
}
