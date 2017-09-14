package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class ItemRequestBuilder {

  private final UUID id;
  private final String title;
  private final String barcode;

  public ItemRequestBuilder() {
    this(UUID.randomUUID(), "Nod", "565578437802");
  }

  public ItemRequestBuilder(UUID id, String title, String barcode) {
    this.id = id;
    this.title = title;
    this.barcode = barcode;
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

    itemRequest.put("status", new JsonObject().put("name", "Available"));
    itemRequest.put("materialTypeId", APITestSuite.bookMaterialTypeId());
    itemRequest.put("permanentLoanTypeId", APITestSuite.canCirculateLoanTypeId());
    itemRequest.put("location", new JsonObject().put("name", "Main Library"));

    return itemRequest;
  }

  public ItemRequestBuilder withTitle(String title) {
    return new ItemRequestBuilder(
      this.id,
      title,
      this.barcode);
  }

  public ItemRequestBuilder withBarcode(String barcode) {
    return new ItemRequestBuilder(
      this.id,
      this.title,
      barcode);
  }

  public ItemRequestBuilder withNoBarcode() {
    return new ItemRequestBuilder(
      this.id,
      this.title,
      null);
  }
}
