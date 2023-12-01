package api.support.builders;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class CirculationItemsBuilder implements Builder {

  private final JsonObject representation;

  public CirculationItemsBuilder(UUID itemId, String barcode, UUID holdingId, UUID locationId, UUID materialTypeId, UUID loanTypeId, boolean isDcb) {

    this.representation = new JsonObject()
      .put("id", itemId)
      .put("holdingsRecordId", holdingId)
      .put("effectiveLocationId", locationId)
      .put("barcode", barcode)
      .put("materialTypeId", materialTypeId)
      .put("temporaryLoanTypeId", loanTypeId)
      .put("dcbItem", isDcb);
  }

  @Override
  public JsonObject create() {
    return representation;
  }
}
