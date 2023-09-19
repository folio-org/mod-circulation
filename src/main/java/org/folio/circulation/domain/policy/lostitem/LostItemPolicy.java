package org.folio.circulation.domain.policy.lostitem;

import static org.folio.circulation.domain.policy.Period.zeroDurationPeriod;
import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.ACTUAL_COST;
import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.SET_COST;
import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.forValue;
import static org.folio.circulation.domain.policy.lostitem.itemfee.ActualCostFee.noActualCostFee;
import static org.folio.circulation.domain.policy.lostitem.itemfee.AutomaticallyChargeableFee.noAutomaticallyChargeableFee;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.policy.Policy;
import org.folio.circulation.domain.policy.lostitem.itemfee.ActualCostFee;
import org.folio.circulation.domain.policy.lostitem.itemfee.AutomaticallyChargeableFee;
import org.folio.circulation.domain.policy.lostitem.itemfee.ChargeableFee;

import io.vertx.core.json.JsonObject;

public class LostItemPolicy extends Policy {
  private final AutomaticallyChargeableFee declareLostProcessingFee;
  // There is no separate age to lost processing fee but there is a flag
  // that turns on/off the fee, but we're modelling it as a separate fee
  // to simplify logic.
  private final AutomaticallyChargeableFee ageToLostProcessingFee;
  private final AutomaticallyChargeableFee setCostFee;
  private final ChargeableFee actualCostFee;
  private final Period feeRefundInterval;
  private final boolean refundProcessingFeeWhenReturned;
  private final boolean chargeOverdueFine;
  private final Period itemAgedToLostAfterOverdueInterval;
  private final Period patronBilledAfterItemAgedToLostInterval;
  private final Period recalledItemAgedToLostAfterOverdueInterval;
  private final Period patronBilledAfterRecalledItemAgedToLostInterval;
  private static final String CHARGE_AMOUNT_ITEM = "chargeAmountItem";
  private static final String CHARGE_TYPE = "chargeType";
  private final Period lostItemChargeFeeFineInterval;

  private LostItemPolicy(String id, String name,
    AutomaticallyChargeableFee declareLostProcessingFee,
    AutomaticallyChargeableFee ageToLostProcessingFee, AutomaticallyChargeableFee setCostFee,
    ChargeableFee actualCostFee, Period feeRefundInterval, boolean refundProcessingFeeWhenFound,
    boolean chargeOverdueFine, Period itemAgedToLostAfterOverdueInterval,
    Period patronBilledAfterItemAgedToLostInterval,
    Period recalledItemAgedToLostAfterOverdueInterval,
    Period patronBilledAfterRecalledItemAgedToLostInterval,
    Period lostItemChargeFeeFineInterval) {

    super(id, name);
    this.declareLostProcessingFee = declareLostProcessingFee;
    this.ageToLostProcessingFee = ageToLostProcessingFee;
    this.setCostFee = setCostFee;
    this.actualCostFee = actualCostFee;
    this.feeRefundInterval = feeRefundInterval;
    this.refundProcessingFeeWhenReturned = refundProcessingFeeWhenFound;
    this.chargeOverdueFine = chargeOverdueFine;
    this.itemAgedToLostAfterOverdueInterval = itemAgedToLostAfterOverdueInterval;
    this.patronBilledAfterItemAgedToLostInterval = patronBilledAfterItemAgedToLostInterval;
    this.recalledItemAgedToLostAfterOverdueInterval = recalledItemAgedToLostAfterOverdueInterval;
    this.patronBilledAfterRecalledItemAgedToLostInterval =
      patronBilledAfterRecalledItemAgedToLostInterval;
    this.lostItemChargeFeeFineInterval = lostItemChargeFeeFineInterval;
  }

  public static LostItemPolicy from(JsonObject lostItemPolicy) {
    return new LostItemPolicy(
      getProperty(lostItemPolicy, "id"),
      getProperty(lostItemPolicy, "name"),
      getProcessingFee(lostItemPolicy, "chargeAmountItemPatron"),
      getProcessingFee(lostItemPolicy, "chargeAmountItemSystem"),
      getSetCostFee(lostItemPolicy),
      getActualCostFee(lostItemPolicy),
      getPeriodPropertyOrEmpty(lostItemPolicy, "feesFinesShallRefunded"),
      getBooleanProperty(lostItemPolicy, "returnedLostItemProcessingFee"),
      getChargeOverdueFineProperty(lostItemPolicy),
      getPeriodPropertyOrEmpty(lostItemPolicy, "itemAgedLostOverdue"),
      getPeriodPropertyOrEmpty(lostItemPolicy, "patronBilledAfterAgedLost"),
      getPeriodPropertyOrEmpty(lostItemPolicy, "recalledItemAgedLostOverdue"),
      getPeriodPropertyOrEmpty(lostItemPolicy, "patronBilledAfterRecalledItemAgedLost"),
      getPeriodPropertyOrEmpty(lostItemPolicy, "lostItemChargeFeeFine")
    );
  }

  public static Period getPeriodPropertyOrEmpty(JsonObject json, String propertyName) {
    if (json == null || !json.containsKey(propertyName)) {
      return zeroDurationPeriod();
    }

    return Period.from(json.getJsonObject(propertyName));
  }

  private static boolean getChargeOverdueFineProperty(JsonObject lostItemPolicy) {
    final String lostItemReturned = lostItemPolicy.getString("lostItemReturned");
    return "charge".equalsIgnoreCase(lostItemReturned);
  }

  private static AutomaticallyChargeableFee getProcessingFee(JsonObject policy, String enabledFlag) {
    final boolean chargeProcessingFee = getBooleanProperty(policy, enabledFlag);
    final BigDecimal amount = getBigDecimalProperty(policy, "lostItemProcessingFee");

    return amount != null && chargeProcessingFee
      ? new AutomaticallyChargeableFee(amount)
      : noAutomaticallyChargeableFee();
  }

  private static AutomaticallyChargeableFee getSetCostFee(JsonObject policy) {
    final JsonObject chargeAmountItem = getObjectProperty(policy, CHARGE_AMOUNT_ITEM);
    final ChargeAmountType chargeType = forValue(getProperty(chargeAmountItem, CHARGE_TYPE));
    final BigDecimal amount = getBigDecimalProperty(chargeAmountItem, "amount");

    return chargeAmountItem != null && chargeType == SET_COST && amount != null
      ? new AutomaticallyChargeableFee(amount)
      : noAutomaticallyChargeableFee();
  }

  private static ChargeableFee getActualCostFee(JsonObject policy) {
    final ChargeAmountType chargeType = forValue(getNestedStringProperty(policy,
      CHARGE_AMOUNT_ITEM, CHARGE_TYPE));

    return chargeType == ACTUAL_COST
      ? new ActualCostFee()
      : noActualCostFee();
  }

  public AutomaticallyChargeableFee getSetCostFee() {
    return setCostFee;
  }

  public AutomaticallyChargeableFee getDeclareLostProcessingFee() {
    return declareLostProcessingFee;
  }

  public ChargeableFee getActualCostFee() {
    return actualCostFee;
  }

  public boolean shouldRefundFees(ZonedDateTime lostDateTime) {
    return feeRefundInterval.hasZeroDuration()
      || feeRefundInterval.hasNotPassedSinceDateTillNow(lostDateTime)
      || feeRefundInterval.isEqualToDateTillNow(lostDateTime);
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

  public boolean canAgeLoanToLost(boolean isRecalled, ZonedDateTime loanDueDate) {
    final Period periodShouldPassSinceOverdue = isRecalled
      ? recalledItemAgedToLostAfterOverdueInterval : itemAgedToLostAfterOverdueInterval;

    if (periodShouldPassSinceOverdue.hasZeroDuration()) {
      return false;
    }

    return periodShouldPassSinceOverdue.hasPassedSinceDateTillNow(loanDueDate);
  }

  public ZonedDateTime calculateFeeFineChargingPeriodExpirationDateTime(ZonedDateTime lostTime) {

    if (Period.zeroDurationPeriod().equals(lostItemChargeFeeFineInterval)) {
      return null;
    }

    return lostItemChargeFeeFineInterval.plusDate(lostTime);
  }

  public ZonedDateTime calculateDateTimeWhenPatronBilledForAgedToLost(
    boolean isRecalled, ZonedDateTime ageToLostDate) {

    final Period billAfterPeriod = isRecalled
      ? patronBilledAfterRecalledItemAgedToLostInterval
      : patronBilledAfterItemAgedToLostInterval;

    return billAfterPeriod.plusDate(ageToLostDate);
  }

  public AutomaticallyChargeableFee getAgeToLostProcessingFee() {
    return ageToLostProcessingFee;
  }

  public static LostItemPolicy unknown(String id) {
    return new UnknownLostItemPolicy(id);
  }

  public boolean hasNoLostItemFee() {
    return !hasLostItemFee();
  }

  public boolean hasLostItemFee() {
    return actualCostFee.isChargeable() || setCostFee.isChargeable();
  }

  public boolean hasLostItemProcessingFee() {
    return declareLostProcessingFee.isChargeable();
  }

  private static class UnknownLostItemPolicy extends LostItemPolicy {
    UnknownLostItemPolicy(String id) {
      super(id, null, noAutomaticallyChargeableFee(), noAutomaticallyChargeableFee(),
        noAutomaticallyChargeableFee(), noActualCostFee(), zeroDurationPeriod(), false, false,
        zeroDurationPeriod(), zeroDurationPeriod(), zeroDurationPeriod(),
        zeroDurationPeriod(), zeroDurationPeriod());
    }
  }
}
