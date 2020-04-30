package org.folio.circulation.domain.policy.lostitem;

import java.util.Arrays;

public enum ChargeAmountType {
  SET_COST("anotherCost"),
  ACTUAL_COST("actualCost");

  private final String value;

  ChargeAmountType(String value) {
    this.value = value;
  }

  public static ChargeAmountType forValue(String value) {
    return Arrays.stream(values())
      .filter(entity -> entity.value.equals(value))
      .findFirst()
      .orElse(null);
  }
}
