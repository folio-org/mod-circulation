package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class ItemRequestBuilder {

  private static final String AVAILABLE_STATUS = "Available";
  private static final String CHECKED_OUT_STATUS = "Checked out";

  private final UUID id;
  private final String title;
  private final String barcode;
  private final String status;

  public ItemRequestBuilder() {
    this(UUID.randomUUID(), "Nod", "565578437802", AVAILABLE_STATUS);
  }

  public ItemRequestBuilder(
    UUID id,
    String title,
    String barcode,
    String status) {

    this.id = id;
    this.title = title;
    this.barcode = barcode;
    this.status = status;
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

    itemRequest.put("status", new JsonObject().put("name", status));
    itemRequest.put("materialTypeId", APITestSuite.bookMaterialTypeId());
    itemRequest.put("permanentLoanTypeId", APITestSuite.canCirculateLoanTypeId());
    itemRequest.put("location", new JsonObject().put("name", "Main Library"));

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
      this.title,
      this.barcode,
      status);
  }

  public ItemRequestBuilder withTitle(String title) {
    return new ItemRequestBuilder(
      this.id,
      title,
      this.barcode,
      this.status);
  }

  public ItemRequestBuilder withBarcode(String barcode) {
    return new ItemRequestBuilder(
      this.id,
      this.title,
      barcode,
      this.status);
  }

  public ItemRequestBuilder withNoBarcode() {
    return new ItemRequestBuilder(
      this.id,
      this.title,
      null,
      this.status);
  }
}
