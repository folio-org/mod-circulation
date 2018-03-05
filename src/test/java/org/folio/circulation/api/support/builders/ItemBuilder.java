package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class ItemBuilder implements Builder {

  public static final String AVAILABLE = "Available";
  public static final String CHECKED_OUT = "Checked out";
  public static final String CHECKED_OUT_HELD = "Checked out - Held";
  public static final String CHECKED_OUT_RECALLED = "Checked out - Recalled";
  public static final String AWAITING_PICKUP = "Awaiting pickup";

  private final UUID id;
  private final UUID holdingId;
  private final String barcode;
  private final String status;
  private final UUID materialTypeId;
  private final UUID temporaryLocationId;
  private final UUID temporaryLoanTypeId;

  public ItemBuilder() {
    this(UUID.randomUUID(), null, "565578437802", AVAILABLE,
      null, null, null);
  }

  private ItemBuilder(
    UUID id,
    UUID holdingId,
    String barcode,
    String status,
    UUID temporaryLocationId,
    UUID materialTypeId,
    UUID temporaryLoanTypeId) {

    this.id = id;
    this.holdingId = holdingId;
    this.barcode = barcode;
    this.status = status;
    this.temporaryLocationId = temporaryLocationId;
    this.materialTypeId = materialTypeId;
    this.temporaryLoanTypeId = temporaryLoanTypeId;
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

    if(materialTypeId != null) {
      itemRequest.put("materialTypeId", materialTypeId.toString());
    }

    itemRequest.put("status", new JsonObject().put("name", status));
    itemRequest.put("permanentLoanTypeId", APITestSuite.canCirculateLoanTypeId().toString());

    if(temporaryLocationId != null) {
      itemRequest.put("temporaryLocationId", temporaryLocationId.toString());
    }

    if(temporaryLoanTypeId != null) {
      itemRequest.put("temporaryLoanTypeId", temporaryLoanTypeId.toString());
    }

    return itemRequest;
  }

  public ItemBuilder checkOut() {
    return withStatus(CHECKED_OUT);
  }

  public ItemBuilder available() {
    return withStatus(AVAILABLE);
  }

  public ItemBuilder withStatus(String status) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.temporaryLoanTypeId);
  }

  public ItemBuilder withBarcode(String barcode) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.temporaryLoanTypeId);
  }

  public ItemBuilder withNoBarcode() {
    return withBarcode(null);
  }

  public ItemBuilder withNoTemporaryLocation() {
    return withTemporaryLocation(null);
  }

  public ItemBuilder withTemporaryLocation(UUID temporaryLocationId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      temporaryLocationId,
      this.materialTypeId,
      this.temporaryLoanTypeId);
  }

  public ItemBuilder forHolding(UUID holdingId) {
    return new ItemBuilder(
      this.id,
      holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.temporaryLoanTypeId);
  }

  public ItemBuilder withMaterialType(UUID materialTypeId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      materialTypeId,
      this.temporaryLoanTypeId);
  }

  public ItemBuilder withTemporaryLoanType(UUID loanTypeId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      loanTypeId);

  }
}
