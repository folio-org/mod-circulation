package org.folio.circulation.domain.policy;

import static org.folio.circulation.domain.policy.LostItemPolicyChargeAmountType.forValue;
import static org.folio.circulation.support.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.math.BigDecimal;

import io.vertx.core.json.JsonObject;

public class LostItemPolicyChargeAmountItem {
  private final BigDecimal amount;
  private final LostItemPolicyChargeAmountType chargeType;

  public LostItemPolicyChargeAmountItem(BigDecimal amount, LostItemPolicyChargeAmountType chargeType) {
    this.amount = amount;
    this.chargeType = chargeType;
  }

  public static LostItemPolicyChargeAmountItem from(JsonObject chargeAmountType) {
    return new LostItemPolicyChargeAmountItem(
      getBigDecimalProperty(chargeAmountType, "amount"),
      forValue(getProperty(chargeAmountType, "chargeType")));
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public LostItemPolicyChargeAmountType getChargeType() {
    return chargeType;
  }
}
