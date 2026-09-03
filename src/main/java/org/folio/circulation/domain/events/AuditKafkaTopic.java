package org.folio.circulation.domain.events;

public enum AuditKafkaTopic implements KafkaTopicDefinition {
  LOG_RECORD;

  @Override
  public String moduleName() {
    return "audit";
  }
}
