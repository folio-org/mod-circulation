package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.InventoryRecords;

public class RelatedRecords {
  public final InventoryRecords inventoryRecords;
  public final RequestQueue requestQueue;
  public final JsonObject requestingUser;

  RelatedRecords(
    RequestQueue requestQueue,
    InventoryRecords inventoryRecords,
    JsonObject requestingUser) {

    this.requestQueue = requestQueue;
    this.inventoryRecords = inventoryRecords;
    this.requestingUser = requestingUser;
  }
}
