package org.folio.circulation.domain.policy.lostitem;

import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.ACTUAL_COST;
import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.SET_COST;
import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.forValue;
import static org.folio.circulation.domain.policy.lostitem.itemfee.ActualCostFee.noActualCostFee;
import static org.folio.circulation.domain.policy.lostitem.itemfee.AutomaticallyChargeableFee.noAutomaticallyChargeableFee;
import static org.folio.circulation.support.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.math.BigDecimal;

import org.folio.circulation.domain.policy.Policy;
import org.folio.circulation.domain.policy.lostitem.itemfee.ActualCostFee;
import org.folio.circulation.domain.policy.lostitem.itemfee.AutomaticallyChargeableFee;
import org.folio.circulation.domain.policy.lostitem.itemfee.ChargeableFee;

import io.vertx.core.json.JsonObject;

public class LostItemPolicy extends Policy {
  private final AutomaticallyChargeableFee processingFee;
  private final AutomaticallyChargeableFee setCostFee;
  private final ChargeableFee actualCostFee;

  private LostItemPolicy(String id, String name, AutomaticallyChargeableFee processingFee,
    AutomaticallyChargeableFee setCostFee, ChargeableFee actualCostFee) {

    super(id, name);
    this.processingFee = processingFee;
    this.setCostFee = setCostFee;
    this.actualCostFee = actualCostFee;
  }

  public static LostItemPolicy from(JsonObject lostItemPolicy) {
    return new LostItemPolicy(
      getProperty(lostItemPolicy, "id"),
      getProperty(lostItemPolicy, "name"),
      getProcessingFee(lostItemPolicy),
      getSetCostFee(lostItemPolicy),
      getActualCostFee(lostItemPolicy)
    );
  }

  private static AutomaticallyChargeableFee getProcessingFee(JsonObject policy) {
    final boolean chargeProcessingFee = getBooleanProperty(policy, "chargeAmountItemPatron");
    final BigDecimal amount = getBigDecimalProperty(policy, "lostItemProcessingFee");

    return amount != null && chargeProcessingFee
      ? new AutomaticallyChargeableFee(amount)
      : noAutomaticallyChargeableFee();
  }

  private static AutomaticallyChargeableFee getSetCostFee(JsonObject policy) {
    final JsonObject chargeAmountItem = getObjectProperty(policy, "chargeAmountItem");
    final ChargeAmountType chargeType = forValue(getProperty(chargeAmountItem, "chargeType"));

    return chargeAmountItem != null && chargeType == SET_COST
      ? new AutomaticallyChargeableFee(getBigDecimalProperty(chargeAmountItem, "amount"))
      : noAutomaticallyChargeableFee();
  }

  private static ChargeableFee getActualCostFee(JsonObject policy) {
    final ChargeAmountType chargeType = forValue(getNestedStringProperty(policy,
      "chargeAmountItem", "chargeType"));

    return chargeType == ACTUAL_COST
      ? new ActualCostFee()
      : noActualCostFee();
  }

  public AutomaticallyChargeableFee getSetCostFee() {
    return setCostFee;
  }

  public AutomaticallyChargeableFee getProcessingFee() {
    return processingFee;
  }

  public ChargeableFee getActualCostFee() {
    return actualCostFee;
  }

  public static LostItemPolicy unknown(String id) {
    return new UnknownLostItemPolicy(id);
  }

  private static class UnknownLostItemPolicy extends LostItemPolicy {
    UnknownLostItemPolicy(String id) {
      super(id, null, noAutomaticallyChargeableFee(), noAutomaticallyChargeableFee(),
        noActualCostFee());
    }
  }
}
