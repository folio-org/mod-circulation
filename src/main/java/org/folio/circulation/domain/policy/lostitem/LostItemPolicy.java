package org.folio.circulation.domain.policy.lostitem;

import static java.util.Optional.ofNullable;
import static org.folio.circulation.support.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.math.BigDecimal;
import java.util.Optional;

import org.folio.circulation.domain.policy.Policy;

import io.vertx.core.json.JsonObject;

public class LostItemPolicy extends Policy {
  private final ChargeAmount chargeAmountItem;
  private final boolean chargeAmountItemPatron;
  private final BigDecimal lostItemProcessingFee;

  private LostItemPolicy(String id, String name, ChargeAmount chargeAmountItem,
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
      ChargeAmount.from(getObjectProperty(lostItemPolicy, "chargeAmountItem")),
      getBooleanProperty(lostItemPolicy, "chargeAmountItemPatron"),
      getBigDecimalProperty(lostItemPolicy, "lostItemProcessingFee")
    );
  }

  public Optional<ChargeAmount> getChargeAmountItem() {
    return ofNullable(chargeAmountItem);
  }

  public boolean shouldChargeProcessingFee() {
    // Renamed to better describe intent. Fee/fines domain name quite unclear.
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
      super(id, null, null, false, null);
    }
  }
}
