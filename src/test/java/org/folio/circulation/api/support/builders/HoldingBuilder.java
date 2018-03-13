package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class HoldingBuilder implements Builder {

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
    return new JsonObject()
      .put("instanceId", instanceId.toString())
      .put("permanentLocationId", permanentLocationId.toString())
      .put("callNumber", callNumber);
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
