package org.folio.circulation.domain.policy.lostitem;

import static org.folio.circulation.support.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.math.BigDecimal;

import io.vertx.core.json.JsonObject;

public final class SetCostChargeFee extends ChargeFee {
  public SetCostChargeFee(BigDecimal amount) {
    super(amount);
  }

  public static SetCostChargeFee from(JsonObject chargeItem) {
    final BigDecimal amount = getBigDecimalProperty(chargeItem, "amount");
    final ChargeAmountType chargeType = ChargeAmountType
      .forValue(getProperty(chargeItem, "chargeType"));

    return chargeType == ChargeAmountType.SET_COST
      ? new SetCostChargeFee(amount)
      : new SetCostChargeFee(null);
  }
}
