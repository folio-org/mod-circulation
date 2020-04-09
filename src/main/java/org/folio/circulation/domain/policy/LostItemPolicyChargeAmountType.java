package org.folio.circulation.domain.policy;

import java.util.Arrays;

public enum LostItemPolicyChargeAmountType {
  SET_COST("anotherCost");

  private final String value;

  LostItemPolicyChargeAmountType(String value) {
    this.value = value;
  }

  public static LostItemPolicyChargeAmountType forValue(String value) {
    return Arrays.stream(values())
      .filter(entity -> entity.value.equals(value))
      .findFirst()
      .orElse(null);
  }
}
