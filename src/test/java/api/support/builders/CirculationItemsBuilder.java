package api.support.builders;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class CirculationItemsBuilder extends JsonBuilder implements Builder {

  private final UUID itemId;
  private final String barcode;
  private final UUID holdingId;
  private final UUID locationId;
  private final UUID materialTypeId;
  private final UUID loanTypeId;
  private final boolean isDcb;
  private final String lendingLibraryCode;

  public CirculationItemsBuilder() {
    this(UUID.randomUUID(),
      "036000291452",
      UUID.randomUUID(),
      UUID.randomUUID(),
      UUID.randomUUID(),
      UUID.randomUUID(),
      true,
      "11223");
  }

  private CirculationItemsBuilder(
    UUID itemId,
    String barcode,
    UUID holdingId,
    UUID locationId,
    UUID materialTypeId,
    UUID loanTypeId,
    boolean isDcb,
    String lendingLibraryCode) {

    this.itemId = itemId;
    this.barcode = barcode;
    this.holdingId = holdingId;
    this.locationId = locationId;
    this.materialTypeId = materialTypeId;
    this.loanTypeId = loanTypeId;
    this.isDcb = isDcb;
    this.lendingLibraryCode = lendingLibraryCode;
  }

  public JsonObject create() {
    JsonObject representation = new JsonObject();

    representation.put("id", itemId);
    representation.put("holdingsRecordId", holdingId);
    representation.put("effectiveLocationId", locationId);
    representation.put("barcode", barcode);
    representation.put("materialTypeId", materialTypeId);
    representation.put("temporaryLoanTypeId", loanTypeId);
    representation.put("dcbItem", isDcb);
    representation.put("lendingLibraryCode", lendingLibraryCode);

    return representation;
  }

  public CirculationItemsBuilder withBarcode(String barcode) {
    return new CirculationItemsBuilder(
      this.itemId,
      barcode,
      this.holdingId,
      this.locationId,
      this.materialTypeId,
      this.loanTypeId,
      this.isDcb,
      this.lendingLibraryCode);
  }

  public CirculationItemsBuilder withHoldingId(UUID holdingId) {
    return new CirculationItemsBuilder(
      this.itemId,
      this.barcode,
      holdingId,
      this.locationId,
      this.materialTypeId,
      this.loanTypeId,
      this.isDcb,
      this.lendingLibraryCode);
  }

  public CirculationItemsBuilder withItemId(UUID itemId) {
    return new CirculationItemsBuilder(
      itemId,
      this.barcode,
      this.holdingId,
      this.locationId,
      this.materialTypeId,
      this.loanTypeId,
      this.isDcb,
      this.lendingLibraryCode);
  }

  public CirculationItemsBuilder withLocationId(UUID locationId) {
    return new CirculationItemsBuilder(
      this.itemId,
      this.barcode,
      this.holdingId,
      locationId,
      this.materialTypeId,
      this.loanTypeId,
      this.isDcb,
      this.lendingLibraryCode);
  }

  public CirculationItemsBuilder withLendingLibraryCode(String lendingLibraryCode) {
    return new CirculationItemsBuilder(
      this.itemId,
      this.barcode,
      this.holdingId,
      this.locationId,
      this.materialTypeId,
      this.loanTypeId,
      this.isDcb,
      lendingLibraryCode);
  }

  public CirculationItemsBuilder withLoanType(UUID loanTypeId) {
    return new CirculationItemsBuilder(
      this.itemId,
      this.barcode,
      this.holdingId,
      this.locationId,
      this.materialTypeId,
      loanTypeId,
      this.isDcb,
      this.lendingLibraryCode);
  }

  public CirculationItemsBuilder withMaterialType(UUID materialTypeId) {
    return new CirculationItemsBuilder(
      this.itemId,
      this.barcode,
      this.holdingId,
      this.locationId,
      materialTypeId,
      this.loanTypeId,
      this.isDcb,
      this.lendingLibraryCode);
  }

}
