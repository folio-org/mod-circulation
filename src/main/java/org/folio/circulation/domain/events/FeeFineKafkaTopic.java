package org.folio.circulation.domain.events;

public enum FeeFineKafkaTopic implements KafkaTopicDefinition {
  FEE_FINE_BALANCE_CHANGED,
  LOAN_RELATED_FEE_FINE_CLOSED;

  @Override
  public String moduleName() {
    return "feesfines";
  }
}
