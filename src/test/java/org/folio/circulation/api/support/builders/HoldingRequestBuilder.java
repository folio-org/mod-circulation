package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class HoldingRequestBuilder implements Builder {

  private final UUID instanceId;
  private final UUID permanentLocationId;
  private final String callNumber;

  public HoldingRequestBuilder() {
    this(
      null,
      APITestSuite.mainLibraryLocationId(),
      null);
  }

  private HoldingRequestBuilder(
    UUID instanceId,
    UUID permanentLocationId) {

    this.instanceId = instanceId;
    this.permanentLocationId = permanentLocationId;
    this.callNumber = "";
  }
  
  private HoldingRequestBuilder(
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

  public HoldingRequestBuilder withPermanentLocation(UUID permanentLocationId) {
    return new HoldingRequestBuilder(
      this.instanceId,
      permanentLocationId,
      this.callNumber);
  }

  public HoldingRequestBuilder forInstance(UUID instanceId) {
    return new HoldingRequestBuilder(
      instanceId,
      this.permanentLocationId,
      this.callNumber);
  }
  
  public HoldingRequestBuilder withCallNumber(String callNumber) {
    return new HoldingRequestBuilder(
      this.instanceId,
      this.permanentLocationId,
      callNumber
    );
  }
}
