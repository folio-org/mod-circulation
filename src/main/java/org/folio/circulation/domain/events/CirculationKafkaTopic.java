package org.folio.circulation.domain.events;

public enum CirculationKafkaTopic implements KafkaTopicDefinition {
  ITEM_CHECKED_OUT,
  ITEM_CHECKED_IN,
  ITEM_DECLARED_LOST,
  ITEM_AGED_TO_LOST,
  ITEM_CLAIMED_RETURNED,
  LOAN_DUE_DATE_CHANGED,
  LOAN_CLOSED;

  @Override
  public String moduleName() {
    return "circulation";
  }
}
