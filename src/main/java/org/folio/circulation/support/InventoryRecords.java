package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

public class InventoryRecords {
  private final JsonObject item;
  private final JsonObject holding;
  private final JsonObject instance;

  public InventoryRecords(
    JsonObject item,
    JsonObject holding,
    JsonObject instance) {

    this.item = item;
    this.holding = holding;
    this.instance = instance;
  }

  public JsonObject getItem() {
    return item;
  }

  public JsonObject getHolding() {
    return holding;
  }

  public JsonObject getInstance() {
    return instance;
  }
}
