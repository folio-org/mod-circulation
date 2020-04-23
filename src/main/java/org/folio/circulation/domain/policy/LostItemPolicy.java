package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getDoubleProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;

public class LostItemPolicy extends Policy {

  private final Boolean chargeAmountItemPatron;
  private final Boolean chargeAmountItemSystem;
  private final Charge chargeAmountItem;
  private final Double lostItemProcessingFee;
  private final Period patronBilledAfterAgedLost;

  public LostItemPolicy(Boolean chargeAmountItemPatron, Boolean chargeAmountItemSystem, 
          Charge chargeAmountItem, Double lostItemProcessingFee,
          String id, String name, Period patronBilledAfterAgedLost) {
    super(id, name);
    this.chargeAmountItemPatron = chargeAmountItemPatron;
    this.chargeAmountItemSystem = chargeAmountItemSystem;
    this.chargeAmountItem = chargeAmountItem;
    this.lostItemProcessingFee = lostItemProcessingFee;
    this.patronBilledAfterAgedLost = patronBilledAfterAgedLost;
    
  }

  public static LostItemPolicy from(JsonObject lostItemPolicy) {
    String chargeType = getNestedStringProperty(lostItemPolicy, "chargeAmountItem", "chargeType");
    String amountStr = getNestedStringProperty(lostItemPolicy, "chargeAmountItem", "amount");
    Double amount = (amountStr == null) ? 0 : Double.valueOf(amountStr);
    String durationStr = getNestedStringProperty(lostItemPolicy, "patronBilledAfterAgedLost", "duration");
    String intervalID = getNestedStringProperty(lostItemPolicy, "patronBilledAfterAgedLost", "periodId");
    Integer duration = (durationStr == null) ? 0 : Integer.valueOf(durationStr);
    
    return new LostItemPolicy(
            getBooleanProperty(lostItemPolicy, "chargeAmountItemPatron"),
            getBooleanProperty(lostItemPolicy, "chargeAmountItemSystem"),
            Charge.from(chargeType, amount),
            getDoubleProperty(lostItemPolicy, "lostItemProcessingFee", 0.0),
            getProperty(lostItemPolicy, "id"),
            getProperty(lostItemPolicy, "name"),
            Period.from(duration, intervalID));
    
  }

  public Boolean getChargeAmountItemPatron() {
    return chargeAmountItemPatron;
  }

  public Boolean getChargeAmountItemSystem() {
    return chargeAmountItemSystem;
  }

  public Charge getChargeAmountItem() {
    return chargeAmountItem;
  }

  public Double getLostItemProcessingFee() {
    return lostItemProcessingFee;
  }

  public Period getPatronBilledAfterAgedLost() {
    return patronBilledAfterAgedLost;
  }

  public static LostItemPolicy unknown(String id) {
    return new UnknownLostItemPolicy(id);
  }
  
  public boolean isUnknown() {
    return this instanceof UnknownLostItemPolicy;
  }

  private static class UnknownLostItemPolicy extends LostItemPolicy {

    UnknownLostItemPolicy(String id) {
      super(null, null,null, null, id, null,null);
    }
  }
}
