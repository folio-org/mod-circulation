package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class HoldingBuilder implements Builder {

  private final UUID instanceId;
  private final UUID permanentLocationId;

  public HoldingBuilder() {
    this(
      null,
      APITestSuite.mainLibraryLocationId());
  }

  private HoldingBuilder(
    UUID instanceId,
    UUID permanentLocationId) {

    this.instanceId = instanceId;
    this.permanentLocationId = permanentLocationId;
  }

  @Override
  public JsonObject create() {
    return new JsonObject()
      .put("instanceId", instanceId.toString())
      .put("permanentLocationId", permanentLocationId.toString());
  }

  public HoldingBuilder withPermanentLocation(UUID permanentLocationId) {
    return new HoldingBuilder(
      this.instanceId,
      permanentLocationId);
  }

  public HoldingBuilder forInstance(UUID instanceId) {
    return new HoldingBuilder(
      instanceId,
      this.permanentLocationId);
  }
}
