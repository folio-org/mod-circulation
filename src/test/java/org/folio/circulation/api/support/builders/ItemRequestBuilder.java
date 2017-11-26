package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class ItemRequestBuilder implements Builder {

  private static final String AVAILABLE_STATUS = "Available";
  private static final String CHECKED_OUT_STATUS = "Checked out";

  private final UUID id;
  private final UUID holdingId;
  private final String title;
  private final String barcode;
  private final String status;
  private final UUID permanentLocationId;
  private final UUID temporaryLocationId;

  public ItemRequestBuilder() {
    this(UUID.randomUUID(), null, "Nod", "565578437802", AVAILABLE_STATUS,
      null, null);
  }

  private ItemRequestBuilder(
    UUID id,
    UUID holdingId,
    String title,
    String barcode,
    String status,
    UUID permanentLocationId,
    UUID temporaryLocationId) {

    this.id = id;
    this.holdingId = holdingId;
    this.title = title;
    this.barcode = barcode;
    this.status = status;
    this.permanentLocationId = permanentLocationId;
    this.temporaryLocationId = temporaryLocationId;
  }

  public JsonObject create() {
    JsonObject itemRequest = new JsonObject();

    if(id != null) {
      itemRequest.put("id", id.toString());
    }

    itemRequest.put("title", title);

    if(barcode != null) {
      itemRequest.put("barcode", barcode);
    }

    if(holdingId != null) {
      itemRequest.put("holdingsRecordId", holdingId.toString());
    }

    itemRequest.put("status", new JsonObject().put("name", status));
    itemRequest.put("materialTypeId", APITestSuite.bookMaterialTypeId().toString());
    itemRequest.put("permanentLoanTypeId", APITestSuite.canCirculateLoanTypeId().toString());

    if(permanentLocationId != null) {
      itemRequest.put("permanentLocationId", permanentLocationId.toString());
    }

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
      this.title,
      this.barcode,
      status,
      this.permanentLocationId,
      this.temporaryLocationId);
  }

  public ItemRequestBuilder withTitle(String title) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      title,
      this.barcode,
      this.status,
      this.permanentLocationId,
      this.temporaryLocationId);
  }

  public ItemRequestBuilder withBarcode(String barcode) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.title,
      barcode,
      this.status,
      this.permanentLocationId,
      this.temporaryLocationId);
  }

  public ItemRequestBuilder withNoBarcode() {
    return withBarcode(null);
  }

  public ItemRequestBuilder withNoPermanentLocation() {
    return withPermanentLocation(null);
  }

  public ItemRequestBuilder withNoTemporaryLocation() {
    return withTemporaryLocation(null);
  }

  public ItemRequestBuilder withPermanentLocation(UUID permanentLocationId) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.title,
      this.barcode,
      this.status,
      permanentLocationId,
      this.temporaryLocationId);
  }

  public ItemRequestBuilder withTemporaryLocation(UUID temporaryLocationId) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.title,
      this.barcode,
      this.status,
      this.permanentLocationId,
      temporaryLocationId);
  }

  public ItemRequestBuilder forHolding(UUID holdingId) {
    return new ItemRequestBuilder(
      this.id,
      holdingId,
      this.title,
      this.barcode,
      this.status,
      this.permanentLocationId,
      this.temporaryLocationId);
  }
}
