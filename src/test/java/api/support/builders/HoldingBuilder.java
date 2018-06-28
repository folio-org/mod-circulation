package api.support.builders;

import io.vertx.core.json.JsonObject;
import api.APITestSuite;

import java.util.UUID;

public class HoldingBuilder extends JsonBuilder implements Builder {

  private final UUID instanceId;
  private final UUID permanentLocationId;
  private final String callNumber;

  public HoldingBuilder() {
    this(
      null,
      APITestSuite.mainLibraryLocationId(),
      null);
  }

  private HoldingBuilder(
    UUID instanceId,
    UUID permanentLocationId,
    String callNumber) {
    this.instanceId = instanceId;
    this.permanentLocationId = permanentLocationId;
    this.callNumber = callNumber;
  }

  @Override
  public JsonObject create() {
    final JsonObject holdings = new JsonObject();

    put(holdings, "instanceId", instanceId);
    put(holdings, "permanentLocationId", permanentLocationId);
    put(holdings, "callNumber", callNumber);

    return holdings;
  }

  public HoldingBuilder withPermanentLocation(UUID permanentLocationId) {
    return new HoldingBuilder(
      this.instanceId,
      permanentLocationId,
      this.callNumber);
  }

  public HoldingBuilder forInstance(UUID instanceId) {
    return new HoldingBuilder(
      instanceId,
      this.permanentLocationId,
      this.callNumber);
  }

  public HoldingBuilder withCallNumber(String callNumber) {
    return new HoldingBuilder(
      this.instanceId,
      this.permanentLocationId,
      callNumber
    );
  }
}
