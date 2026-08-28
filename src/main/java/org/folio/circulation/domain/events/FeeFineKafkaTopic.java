package org.folio.circulation.domain.events;

import org.folio.kafka.services.KafkaTopic;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
@AllArgsConstructor
public enum FeeFineKafkaTopic implements KafkaTopic {
  FEE_FINE_BALANCE_CHANGED("FEE_FINE_BALANCE_CHANGED", 10),
  LOAN_RELATED_FEE_FINE_CLOSED("LOAN_RELATED_FEE_FINE_CLOSED", 10);

  @ToString.Include
  @Getter
  private final String topic;
  private final int partitions;

  @Override
  public String moduleName() {
    return "feesfines";
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
