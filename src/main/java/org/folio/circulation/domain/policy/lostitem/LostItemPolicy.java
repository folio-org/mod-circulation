package org.folio.circulation.domain.policy.lostitem;

import static org.folio.circulation.support.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.policy.Policy;

import io.vertx.core.json.JsonObject;

public class LostItemPolicy extends Policy {
  private final SetCostChargeFee setCostChargeFee;
  private final boolean chargeAmountItemPatron;
  private final ProcessingChargeFee processingChargeFee;

  private LostItemPolicy(String id, String name, SetCostChargeFee setCostChargeFee,
    boolean chargeAmountItemPatron, ProcessingChargeFee processingChargeFee) {

    super(id, name);
    this.setCostChargeFee = setCostChargeFee;
    this.chargeAmountItemPatron = chargeAmountItemPatron;
    this.processingChargeFee = processingChargeFee;
  }

  public static LostItemPolicy from(JsonObject lostItemPolicy) {
    final ProcessingChargeFee lostItemProcessingFee =
      new ProcessingChargeFee(getBigDecimalProperty(lostItemPolicy, "lostItemProcessingFee"));
    final SetCostChargeFee setCostCharge = SetCostChargeFee
      .from(getObjectProperty(lostItemPolicy, "chargeAmountItem"));

    return new LostItemPolicy(
      getProperty(lostItemPolicy, "id"),
      getProperty(lostItemPolicy, "name"),
      setCostCharge,
      getBooleanProperty(lostItemPolicy, "chargeAmountItemPatron"),
      lostItemProcessingFee
    );
  }

  /**
   * Returns wrapper around chargeItem object. SetCostChargeFee#getAmount is null
   * when chargeItem is not of anotherCost type.
   */
  public SetCostChargeFee getSetCostChargeFee() {
    return setCostChargeFee;
  }

  public boolean shouldChargeProcessingFee() {
    // Renamed to better describe intent. Fee/fines domain name quite unclear.
    return chargeAmountItemPatron;
  }

  /**
   * Returns wrapper around processing fee amount. ProcessingChargeFee#getAmount
   * is null when no fee defined.
   */
  public ProcessingChargeFee getLostItemProcessingFee() {
    return processingChargeFee;
  }

  public static LostItemPolicy unknown(String id) {
    return new UnknownLostItemPolicy(id);
  }

  private static class UnknownLostItemPolicy extends LostItemPolicy {
    UnknownLostItemPolicy(String id) {
      super(id, null, new SetCostChargeFee(null),
        false, new ProcessingChargeFee(null));
    }
  }
}
