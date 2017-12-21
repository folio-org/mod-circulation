package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class ItemRequestBuilder implements Builder {

  private static final String AVAILABLE_STATUS = "Available";
  private static final String CHECKED_OUT_STATUS = "Checked out";

  private final UUID id;
  private final UUID holdingId;
  private final String barcode;
  private final String status;
  private final UUID temporaryLocationId;

  public ItemRequestBuilder() {
    this(UUID.randomUUID(), null, "565578437802", AVAILABLE_STATUS,
      null);
  }

  private ItemRequestBuilder(
    UUID id,
    UUID holdingId,
    String barcode,
    String status,
    UUID temporaryLocationId) {

    this.id = id;
    this.holdingId = holdingId;
    this.barcode = barcode;
    this.status = status;
    this.temporaryLocationId = temporaryLocationId;
  }

  public JsonObject create() {
    JsonObject itemRequest = new JsonObject();

    if(id != null) {
      itemRequest.put("id", id.toString());
    }

    if(barcode != null) {
      itemRequest.put("barcode", barcode);
    }

    if(holdingId != null) {
      itemRequest.put("holdingsRecordId", holdingId.toString());
    }

    itemRequest.put("status", new JsonObject().put("name", status));
    itemRequest.put("materialTypeId", APITestSuite.bookMaterialTypeId().toString());
    itemRequest.put("permanentLoanTypeId", APITestSuite.canCirculateLoanTypeId().toString());

    if(temporaryLocationId != null) {
      itemRequest.put("temporaryLocationId", temporaryLocationId.toString());
    }

    return itemRequest;
  }

  public ItemRequestBuilder checkOut() {
    return withStatus(CHECKED_OUT_STATUS);
  }

  public ItemRequestBuilder available() {
    return withStatus(AVAILABLE_STATUS);
  }

  public ItemRequestBuilder withStatus(String status) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      status,
      this.temporaryLocationId);
  }

  public ItemRequestBuilder withBarcode(String barcode) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      barcode,
      this.status,
      this.temporaryLocationId);
  }

  public ItemRequestBuilder withNoBarcode() {
    return withBarcode(null);
  }

  public ItemRequestBuilder withNoTemporaryLocation() {
    return withTemporaryLocation(null);
  }

  public ItemRequestBuilder withTemporaryLocation(UUID temporaryLocationId) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      temporaryLocationId);
  }

  public ItemRequestBuilder forHolding(UUID holdingId) {
    return new ItemRequestBuilder(
      this.id,
      holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId);
  }
}
