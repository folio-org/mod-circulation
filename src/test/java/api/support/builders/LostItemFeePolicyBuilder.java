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
  private final Period patronBilledAfterAgedLost;
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
  private final JsonObject feeRefundInterval;

  public LostItemFeePolicyBuilder() {
    this(UUID.randomUUID(),
      "Undergrad standard",
      "This is description for undergrad standard",
      null,
      null,
      new JsonObject(),
      5.00,
      true,
      true,
      new JsonObject(),
      true,
      true,
      1.00,
      true,
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

  public LostItemFeePolicyBuilder chargeProcessingFee(Double amount) {
    return withLostItemProcessingFee(amount)
      .withChargeAmountItemPatron(true);
  }

  public LostItemFeePolicyBuilder doNotChargeProcessingFee() {
    return withLostItemProcessingFee(0.0)
      .withChargeAmountItemPatron(false);
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

  private LostItemFeePolicyBuilder withFeeRefundInterval(int duration, String intervalId) {
    return withFeeRefundInterval(Period.from(duration, intervalId).asJson());
  }

  public LostItemFeePolicyBuilder refundFeesWithinMinutes(int duration) {
    return withFeeRefundInterval(duration, "Minutes");
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

    if (patronBilledAfterAgedLost != null) {
      request.put("patronBilledAfterAgedLost", patronBilledAfterAgedLost.asJson());
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
    put(request, "feesFinesShallRefunded", this.feeRefundInterval);
    return request;
  }
}
