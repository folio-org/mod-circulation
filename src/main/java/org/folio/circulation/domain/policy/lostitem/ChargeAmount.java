package org.folio.circulation.domain.policy.lostitem;

import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.forValue;
import static org.folio.circulation.support.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.math.BigDecimal;

import io.vertx.core.json.JsonObject;

public class ChargeAmount {
  private final BigDecimal amount;
  private final ChargeAmountType chargeType;

  public ChargeAmount(BigDecimal amount, ChargeAmountType chargeType) {
    this.amount = amount;
    this.chargeType = chargeType;
  }

  public static ChargeAmount from(JsonObject chargeAmountType) {
    return new ChargeAmount(
      getBigDecimalProperty(chargeAmountType, "amount"),
      forValue(getProperty(chargeAmountType, "chargeType")));
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public ChargeAmountType getChargeType() {
    return chargeType;
  }
}
