package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.math.BigDecimal;

import io.vertx.core.json.JsonObject;

public class LostItemPolicy extends Policy {
  private final LostItemPolicyChargeAmountItem chargeAmountItem;
  private final boolean chargeAmountItemPatron;
  private final BigDecimal lostItemProcessingFee;

  private LostItemPolicy(String id, String name, LostItemPolicyChargeAmountItem chargeAmountItem,
    boolean chargeAmountItemPatron, BigDecimal lostItemProcessingFee) {

    super(id, name);
    this.chargeAmountItem = chargeAmountItem;
    this.chargeAmountItemPatron = chargeAmountItemPatron;
    this.lostItemProcessingFee = lostItemProcessingFee;
  }

  public static LostItemPolicy from(JsonObject lostItemPolicy) {
    return new LostItemPolicy(
      getProperty(lostItemPolicy, "id"),
      getProperty(lostItemPolicy, "name"),
      LostItemPolicyChargeAmountItem.from(getObjectProperty(lostItemPolicy, "chargeAmountItem")),
      getBooleanProperty(lostItemPolicy, "chargeAmountItemPatron"),
      getBigDecimalProperty(lostItemPolicy, "lostItemProcessingFee")
    );
  }

  public LostItemPolicyChargeAmountItem getChargeAmountItem() {
    return chargeAmountItem;
  }

  public boolean isChargeAmountItemPatron() {
    return chargeAmountItemPatron;
  }

  public BigDecimal getLostItemProcessingFee() {
    return lostItemProcessingFee;
  }

  public static LostItemPolicy unknown(String id) {
    return new UnknownLostItemPolicy(id);
  }

  private static class UnknownLostItemPolicy extends LostItemPolicy {
    UnknownLostItemPolicy(String id) {
      super(id, null, new LostItemPolicyChargeAmountItem(null, null),
        false, null);
    }
  }
}
