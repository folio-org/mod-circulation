package org.folio.circulation.domain.policy.lostitem;

import static java.util.Optional.ofNullable;
import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.SET_COST;
import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.forValue;
import static org.folio.circulation.support.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.math.BigDecimal;
import java.util.Optional;

import org.folio.circulation.domain.policy.Policy;

import io.vertx.core.json.JsonObject;

public class LostItemPolicy extends Policy {
  private final ItemFee setCostChargeFee;
  private final boolean chargeAmountItemPatron;
  private final ItemFee processingChargeFee;

  private LostItemPolicy(String id, String name, ItemFee setCostChargeFee,
    boolean chargeAmountItemPatron, ItemFee processingChargeFee) {

    super(id, name);
    this.setCostChargeFee = setCostChargeFee;
    this.chargeAmountItemPatron = chargeAmountItemPatron;
    this.processingChargeFee = processingChargeFee;
  }

  public static LostItemPolicy from(JsonObject lostItemPolicy) {
    return new LostItemPolicy(
      getProperty(lostItemPolicy, "id"),
      getProperty(lostItemPolicy, "name"),
      getSetCostFee(lostItemPolicy),
      getBooleanProperty(lostItemPolicy, "chargeAmountItemPatron"),
      getProcessingFee(lostItemPolicy)
    );
  }

  private static ItemFee getProcessingFee(JsonObject policy) {
    final BigDecimal amount = getBigDecimalProperty(policy, "lostItemProcessingFee");
    return amount != null
      ? new ItemFee(amount)
      : null;
  }

  private static ItemFee getSetCostFee(JsonObject policy) {
    final JsonObject chargeAmountItem = getObjectProperty(policy, "chargeAmountItem");
    final ChargeAmountType chargeType = forValue(getProperty(chargeAmountItem, "chargeType"));

    if (chargeAmountItem == null || chargeType != SET_COST) {
      return null;
    }

    return new ItemFee(getBigDecimalProperty(chargeAmountItem, "amount"));
  }

  public Optional<ItemFee> getSetCostChargeFee() {
    return ofNullable(setCostChargeFee);
  }

  public boolean shouldChargeProcessingFee() {
    // Renamed to better describe intent. Fee/fines domain name quite unclear.
    return chargeAmountItemPatron;
  }

  public Optional<ItemFee> getLostItemProcessingFee() {
    return ofNullable(processingChargeFee);
  }

  public static LostItemPolicy unknown(String id) {
    return new UnknownLostItemPolicy(id);
  }

  private static class UnknownLostItemPolicy extends LostItemPolicy {
    UnknownLostItemPolicy(String id) {
      super(id, null, null, false, null);
    }
  }
}
