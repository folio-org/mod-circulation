package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class FeeFineBuilder extends JsonBuilder implements Builder {
  private UUID id;
  private UUID ownerId;
  private String feeFineType;
  private Double defaultAmount;
  private String chargeNoticeId;
  private String actionNoticeId;

  public FeeFineBuilder() {
  }

  public FeeFineBuilder(UUID id, UUID ownerId, String feeFineType, Double defaultAmount,
    String chargeNoticeId, String actionNoticeId) {
    this.id = id;
    this.ownerId = ownerId;
    this.feeFineType = feeFineType;
    this.defaultAmount = defaultAmount;
    this.chargeNoticeId = chargeNoticeId;
    this.actionNoticeId = actionNoticeId;
  }

  @Override
  public JsonObject create() {
    JsonObject object = new JsonObject();

    write(object, "id", id);
    write(object, "ownerId", ownerId);
    write(object, "feeFineType", feeFineType);
    write(object, "defaultAmount", defaultAmount);
    write(object, "chargeNoticeId", chargeNoticeId);
    write(object, "actionNoticeId", actionNoticeId);

    return object;
  }

  public FeeFineBuilder withId(UUID id) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId);
  }
  public FeeFineBuilder withOwnerId(UUID ownerId) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId);
  }
  public FeeFineBuilder withFeeFineType(String feeFineType) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId);
  }
  public FeeFineBuilder withDefaultAmount(Double defaultAmount) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId);
  }
  public FeeFineBuilder withChargeNoticeId(String chargeNoticeId) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId);
  }
  public FeeFineBuilder withActionNoticeId(String actionNoticeId) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId);
  }
  public FeeFineBuilder withMetadata(JsonObject metadata) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId);
  }
}
