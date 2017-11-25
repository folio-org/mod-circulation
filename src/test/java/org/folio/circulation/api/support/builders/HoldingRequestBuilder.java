package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class HoldingRequestBuilder implements Builder {

  private final UUID instanceId;
  private final UUID permanentLocationId;

  public HoldingRequestBuilder(UUID instanceId) {
    this(
      instanceId,
      APITestSuite.mainLibraryLocationId());
  }

  private HoldingRequestBuilder(
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

  public HoldingRequestBuilder withPermanentLocation(UUID permanentLocationId) {
    return new HoldingRequestBuilder(this.instanceId, permanentLocationId);
  }
}
