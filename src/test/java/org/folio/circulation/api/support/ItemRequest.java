package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class ItemRequest {
  public static JsonObject create(
    UUID id,
    String title,
    String barcode) {

    JsonObject itemToCreate = new JsonObject();

    if(id != null) {
      itemToCreate.put("id", id.toString());
    }

    itemToCreate.put("title", title);

    if(barcode != null) {
      itemToCreate.put("barcode", barcode);
    }

    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", APITestSuite.bookMaterialTypeId());
    itemToCreate.put("permanentLoanTypeId", APITestSuite.canCirculateLoanTypeId());
    itemToCreate.put("location", new JsonObject().put("name", "Main Library"));

    return itemToCreate;
  }
}
