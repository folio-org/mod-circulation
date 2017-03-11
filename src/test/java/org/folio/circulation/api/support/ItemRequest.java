package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class ItemRequest {
  public static JsonObject create(
    UUID id,
    UUID instanceId,
    String title,
    String barcode) {

    JsonObject itemToCreate = new JsonObject();

    if(id != null) {
      itemToCreate.put("id", id.toString());
    }

    itemToCreate.put("instanceId", instanceId.toString());
    itemToCreate.put("title", title);
    itemToCreate.put("barcode", barcode);
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialType", new JsonObject().put("name", "Book"));
    itemToCreate.put("location", new JsonObject().put("name", "Main Library"));

    return itemToCreate;
  }
}
