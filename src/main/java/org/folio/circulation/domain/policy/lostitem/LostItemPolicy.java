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

import org.folio.circulation.domain.TimePeriod;
import org.folio.circulation.domain.policy.Policy;
import org.folio.circulation.domain.policy.lostitem.itemfee.ActualCostFee;
import org.folio.circulation.domain.policy.lostitem.itemfee.AutomaticallyChargeableFee;
import org.folio.circulation.domain.policy.lostitem.itemfee.ChargeableFee;
import org.folio.circulation.support.ClockManager;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class LostItemPolicy extends Policy {
  private final AutomaticallyChargeableFee processingFee;
  private final AutomaticallyChargeableFee setCostFee;
  private final ChargeableFee actualCostFee;
  private final TimePeriod feeRefundInterval;
  private final boolean refundProcessingFeeWhenReturned;
  private final boolean chargeOverdueFine;

  private LostItemPolicy(String id, String name, AutomaticallyChargeableFee processingFee,
    AutomaticallyChargeableFee setCostFee, ChargeableFee actualCostFee,
    TimePeriod feeRefundInterval, boolean refundProcessingFeeWhenFound, boolean chargeOverdueFine) {

    super(id, name);
    this.processingFee = processingFee;
    this.setCostFee = setCostFee;
    this.actualCostFee = actualCostFee;
    this.feeRefundInterval = feeRefundInterval;
    this.refundProcessingFeeWhenReturned = refundProcessingFeeWhenFound;
    this.chargeOverdueFine = chargeOverdueFine;
  }

  public static LostItemPolicy from(JsonObject lostItemPolicy) {
    return new LostItemPolicy(
      getProperty(lostItemPolicy, "id"),
      getProperty(lostItemPolicy, "name"),
      getProcessingFee(lostItemPolicy),
      getSetCostFee(lostItemPolicy),
      getActualCostFee(lostItemPolicy),
      getFeeRefundInterval(lostItemPolicy),
      getBooleanProperty(lostItemPolicy, "returnedLostItemProcessingFee"),
      getChargeOverdueFineProperty(lostItemPolicy)
    );
  }

  private static boolean getChargeOverdueFineProperty(JsonObject lostItemPolicy) {
    final String lostItemReturned = lostItemPolicy.getString("lostItemReturned");
    return "charge".equalsIgnoreCase(lostItemReturned);
  }

  private static TimePeriod getFeeRefundInterval(JsonObject policy) {
    final JsonObject feesFinesShallRefunded = policy.getJsonObject("feesFinesShallRefunded");

    return feesFinesShallRefunded != null
      ? TimePeriod.from(feesFinesShallRefunded)
      : null;
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
    final BigDecimal amount = getBigDecimalProperty(chargeAmountItem, "amount");

    return chargeAmountItem != null && chargeType == SET_COST && amount != null
      ? new AutomaticallyChargeableFee(amount)
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

  public boolean shouldRefundFees(DateTime lostDateTime) {
    final DateTime now = ClockManager.getClockManager().getDateTime();

    return feeRefundInterval == null || feeRefundInterval
      .between(lostDateTime, now) <= feeRefundInterval.getDuration();
  }

  public boolean isRefundProcessingFeeWhenReturned() {
    return refundProcessingFeeWhenReturned;
  }

  public boolean shouldChargeOverdueFee() {
    return chargeOverdueFine;
  }


  public boolean hasActualCostFee() {
    return getActualCostFee().isChargeable();
  }

  public static LostItemPolicy unknown(String id) {
    return new UnknownLostItemPolicy(id);
  }

  private static class UnknownLostItemPolicy extends LostItemPolicy {
    UnknownLostItemPolicy(String id) {
      super(id, null, noAutomaticallyChargeableFee(), noAutomaticallyChargeableFee(),
        noActualCostFee(), null, false, false);
    }
  }
}
