package org.folio.circulation.domain.events;

public enum CirculationStorageKafkaTopic implements EnumKafkaTopic {
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
