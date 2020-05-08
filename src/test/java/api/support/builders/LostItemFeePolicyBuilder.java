package api.support.builders;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class LostItemFeePolicyBuilder extends JsonBuilder implements Builder {

  private final UUID id;
  private final String name;
  private final String description;
  private final JsonObject itemAgedLostOverdue;
  private final JsonObject patronBilledAfterAgedLost;
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
      new JsonObject(),
      new JsonObject(),
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

  public LostItemFeePolicyBuilder(
    UUID id,
    String name,
    String description,
    JsonObject itemAgedLostOverdue,
    JsonObject patronBilledAfterAgedLost,
    JsonObject chargeAmountItem,
    Double lostItemProcessingFee,
    boolean chargeAmountItemPatron,
    boolean chargeAmountItemSystem,
    JsonObject lostItemChargeFeeFine,
    boolean returnedLostItemProcessingFee,
    boolean replacedLostItemProcessingFee,
    double replacementProcessingFee,
    boolean replacementAllowed,
    String lostItemReturned,
    JsonObject feeRefundInterval) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.itemAgedLostOverdue = itemAgedLostOverdue;
    this.patronBilledAfterAgedLost = patronBilledAfterAgedLost;
    this.chargeAmountItem = chargeAmountItem;
    this.lostItemProcessingFee = lostItemProcessingFee;
    this.chargeAmountItemPatron = chargeAmountItemPatron;
    this.chargeAmountItemSystem = chargeAmountItemSystem;
    this.lostItemChargeFeeFine = lostItemChargeFeeFine;
    this.returnedLostItemProcessingFee = returnedLostItemProcessingFee;
    this.replacedLostItemProcessingFee = replacedLostItemProcessingFee;
    this.replacementProcessingFee = replacementProcessingFee;
    this.replacementAllowed = replacementAllowed;
    this.lostItemReturned = lostItemReturned;
    this.feeRefundInterval = feeRefundInterval;
  }

  public LostItemFeePolicyBuilder withName(String name) {
    return new LostItemFeePolicyBuilder(
      this.id,
      name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder withId(UUID id) {
    return new LostItemFeePolicyBuilder(
      id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder withDescription(String description) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder withItemAgedLostOverdue(JsonObject itemAgedLostOverdue) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder withPatronBilledAfterAgedLost(JsonObject patronBilledAfterAgedLost) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder withChargeAmountItem(JsonObject chargeAmountItem) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
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

  public LostItemFeePolicyBuilder withLostItemProcessingFee(Double lostItemProcessingFee) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder chargeProcessingFee() {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      true,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder doNotChargeProcessingFee() {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      false,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder withChargeAmountItemSystem(boolean chargeAmountItemSystem) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder withLostItemChargeFeeFine(JsonObject lostItemChargeFeeFine) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  private LostItemFeePolicyBuilder withReturnedLostItemProcessingFee(boolean returnedLostItemProcessingFee) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder refundProcessingFeeWhenReturned() {
    return withReturnedLostItemProcessingFee(true);
  }

  public LostItemFeePolicyBuilder doNotRefundProcessingFeeWhenReturned() {
    return withReturnedLostItemProcessingFee(false);
  }

  public LostItemFeePolicyBuilder withReplacedLostItemProcessingFee(boolean replacedLostItemProcessingFee) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder withReplacementProcessingFee(double replacementProcessingFee) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder withReplacementAllowed(boolean replacementAllowed) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      replacementAllowed,
      this.lostItemReturned,
      this.feeRefundInterval);
  }

  private LostItemFeePolicyBuilder withLostItemReturned(String lostItemReturned) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      lostItemReturned,
      this.feeRefundInterval);
  }

  public LostItemFeePolicyBuilder chargeOverdueFineWhenReturned() {
    return withLostItemReturned("Charge");
  }

  public LostItemFeePolicyBuilder doNotChargeOverdueFineWhenReturned() {
    return withLostItemReturned("Remove");
  }

  private LostItemFeePolicyBuilder withFeeRefundInterval(int duration, String intervalId) {
    return new LostItemFeePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.itemAgedLostOverdue,
      this.patronBilledAfterAgedLost,
      this.chargeAmountItem,
      this.lostItemProcessingFee,
      this.chargeAmountItemPatron,
      this.chargeAmountItemSystem,
      this.lostItemChargeFeeFine,
      this.returnedLostItemProcessingFee,
      this.replacedLostItemProcessingFee,
      this.replacementProcessingFee,
      this.replacementAllowed,
      this.lostItemReturned,
      new JsonObject().put("intervalId", intervalId).put("duration", duration));
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

    put(request, "name", this.name);
    put(request, "description", this.description);
    put(request, "itemAgedLostOverdue", this.itemAgedLostOverdue);
    put(request, "patronBilledAfterAgedLost", this.patronBilledAfterAgedLost);
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
