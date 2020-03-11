package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class FeeFineBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final UUID ownerId;
  private final String feeFineType;
  private final Double defaultAmount;
  private final String chargeNoticeId;
  private final String actionNoticeId;
  private final Boolean automatic;

  public FeeFineBuilder() {
    this.id = null;
    this.ownerId = null;
    this.feeFineType = null;
    this.defaultAmount = null;
    this.chargeNoticeId = null;
    this.actionNoticeId = null;
    this.automatic = null;
  }

  public FeeFineBuilder(UUID id, UUID ownerId, String feeFineType, Double defaultAmount,
    String chargeNoticeId, String actionNoticeId, Boolean automatic) {
    this.id = id;
    this.ownerId = ownerId;
    this.feeFineType = feeFineType;
    this.defaultAmount = defaultAmount;
    this.chargeNoticeId = chargeNoticeId;
    this.actionNoticeId = actionNoticeId;
    this.automatic = automatic;
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
    write(object, "automatic", automatic);

    return object;
  }

  public FeeFineBuilder withId(UUID id) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId, automatic);
  }
  public FeeFineBuilder withOwnerId(UUID ownerId) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId, automatic);
  }
  public FeeFineBuilder withFeeFineType(String feeFineType) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId, automatic);
  }
  public FeeFineBuilder withDefaultAmount(Double defaultAmount) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId, automatic);
  }
  public FeeFineBuilder withChargeNoticeId(String chargeNoticeId) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId, automatic);
  }
  public FeeFineBuilder withActionNoticeId(String actionNoticeId) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId, automatic);
  }
  public FeeFineBuilder withMetadata(JsonObject metadata) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId, automatic);
  }

  public FeeFineBuilder withAutomatic(Boolean automatic) {
    return new FeeFineBuilder(id, ownerId, feeFineType, defaultAmount, chargeNoticeId,
      actionNoticeId, automatic);
  }
}
