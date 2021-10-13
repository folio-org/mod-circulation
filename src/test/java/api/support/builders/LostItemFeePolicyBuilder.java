package api.support.builders;

import java.util.UUID;

import org.folio.circulation.domain.policy.Period;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.With;

@With
@AllArgsConstructor
@ToString
public class LostItemFeePolicyBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String name;
  private final String description;
  private final Period itemAgedToLostAfterOverdue;
  private final Period patronBilledAfterItemAgedToLost;
  private final Period recalledItemAgedToLostAfterOverdue;
  private final Period patronBilledAfterRecalledItemAgedLost;
  private final JsonObject chargeAmountItem;
  private final Double lostItemProcessingFee;
  private final boolean chargeAmountItemPatron;
  private final boolean chargeAmountItemSystem;
  private final JsonObject lostItemChargeFeeFine;
  private final boolean returnedLostItemProcessingFee;
  private final boolean replacedLostItemProcessingFee;
  private final double replacementProcessingFee;
  private final boolean replacementAllowed;
  private final String lostItemReturned;
  private final Period feeRefundInterval;

  public LostItemFeePolicyBuilder() {
    this(UUID.randomUUID(),
      "Undergrad standard",
      "This is description for undergrad standard",
      null,
      null,
      null,
      null,
      new JsonObject(),
      null,
      false,
      false,
      new JsonObject(),
      false,
      false,
      0.0,
      false,
      "Charge",
      null);
  }

  public LostItemFeePolicyBuilder withNoChargeAmountItem() {
    return withChargeAmountItem(null);
  }

  private LostItemFeePolicyBuilder withChargeAmountItem(String chargeType, Double amount) {
    return withChargeAmountItem(new JsonObject()
      .put("amount", amount)
      .put("chargeType", chargeType));
  }

  public LostItemFeePolicyBuilder withSetCost(Double amount) {
    return withChargeAmountItem("anotherCost", amount);
  }

  public LostItemFeePolicyBuilder withActualCost(Double amount) {
    return withChargeAmountItem("actualCost", amount);
  }

  public LostItemFeePolicyBuilder chargeProcessingFeeWhenDeclaredLost(Double amount) {
    return withLostItemProcessingFee(amount)
      .withChargeAmountItemPatron(true);
  }

  public LostItemFeePolicyBuilder chargeProcessingFeeWhenAgedToLost(Double amount) {
    return withLostItemProcessingFee(amount)
      .withChargeAmountItemSystem(true);
  }

  public LostItemFeePolicyBuilder doNotChargeProcessingFeeWhenDeclaredLost() {
    return withLostItemProcessingFee(0.0)
      .withChargeAmountItemPatron(false);
  }

  public LostItemFeePolicyBuilder doNotChargeProcessingFeeWhenAgedToLost() {
    return withLostItemProcessingFee(0.0)
      .withChargeAmountItemSystem(false);
  }

  public LostItemFeePolicyBuilder refundProcessingFeeWhenReturned() {
    return withReturnedLostItemProcessingFee(true);
  }

  public LostItemFeePolicyBuilder doNotRefundProcessingFeeWhenReturned() {
    return withReturnedLostItemProcessingFee(false);
  }

  public LostItemFeePolicyBuilder chargeOverdueFineWhenReturned() {
    return withLostItemReturned("Charge");
  }

  public LostItemFeePolicyBuilder doNotChargeOverdueFineWhenReturned() {
    return withLostItemReturned("Remove");
  }

  public LostItemFeePolicyBuilder withNoFeeRefundInterval() {
    return withFeeRefundInterval(null);
  }

  private LostItemFeePolicyBuilder withFeeRefundInterval(long duration, String intervalId) {
    return withFeeRefundInterval(Period.from(duration, intervalId));
  }

  public LostItemFeePolicyBuilder refundFeesWithinMinutes(long duration) {
    return withFeeRefundInterval(duration, "Minutes");
  }

  public LostItemFeePolicyBuilder refundFeesWithinWeeks(long duration) {
    return withFeeRefundInterval(duration, "Weeks");
  }

  public LostItemFeePolicyBuilder billPatronImmediatelyWhenAgedToLost() {
    return withPatronBilledAfterItemAgedToLost(Period.minutes(0));
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    if (id != null) {
      put(request, "id", id.toString());
    }

    if (itemAgedToLostAfterOverdue != null) {
      request.put("itemAgedLostOverdue", itemAgedToLostAfterOverdue.asJson());
    }

    if (patronBilledAfterItemAgedToLost != null) {
      request.put("patronBilledAfterAgedLost", patronBilledAfterItemAgedToLost.asJson());
    }

    if (recalledItemAgedToLostAfterOverdue != null) {
      request.put("recalledItemAgedLostOverdue", recalledItemAgedToLostAfterOverdue.asJson());
    }

    if (patronBilledAfterRecalledItemAgedLost != null) {
      request.put("patronBilledAfterRecalledItemAgedLost",
        patronBilledAfterRecalledItemAgedLost.asJson());
    }

    if (feeRefundInterval != null) {
      request.put("feesFinesShallRefunded", this.feeRefundInterval.asJson());
    }

    put(request, "name", this.name);
    put(request, "description", this.description);
    put(request, "chargeAmountItem", this.chargeAmountItem);
    put(request, "lostItemProcessingFee", this.lostItemProcessingFee);
    put(request, "chargeAmountItemPatron", this.chargeAmountItemPatron);
    put(request, "chargeAmountItemSystem", this.chargeAmountItemSystem);
    put(request, "lostItemChargeFeeFine", this.lostItemChargeFeeFine);
    put(request, "returnedLostItemProcessingFee", this.returnedLostItemProcessingFee);
    put(request, "replacedLostItemProcessingFee", this.replacedLostItemProcessingFee);
    put(request, "replacementProcessingFee", String.valueOf(this.replacementProcessingFee));
    put(request, "replacementAllowed", this.replacementAllowed);
    put(request, "lostItemReturned", this.lostItemReturned);

    return request;
  }
}
