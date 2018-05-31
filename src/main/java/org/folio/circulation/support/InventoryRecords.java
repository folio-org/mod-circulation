package org.folio.circulation.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonArrayHelper.mapToList;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class InventoryRecords {
  private static final String TITLE_PROPERTY = "title";

  private final JsonObject item;
  private final JsonObject holding;
  private final JsonObject instance;
  private JsonObject location;
  private JsonObject materialType;

  public InventoryRecords(
    JsonObject item,
    JsonObject holding,
    JsonObject instance,
    JsonObject location,
    JsonObject materialType) {

    this.item = item;
    this.holding = holding;
    this.instance = instance;
    this.location = location;
    this.materialType = materialType;
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

  public String getTitle() {
    if(getInstance() != null && getInstance().containsKey(TITLE_PROPERTY)) {
      return getProperty(getInstance(), TITLE_PROPERTY);
    } else if(getItem() != null) {
      return getProperty(getItem(), TITLE_PROPERTY);
    }
    else {
      return null;
    }
  }

  public JsonArray getContributorNames() {
    if(getInstance() == null) {
      return new JsonArray();
    }

    return new JsonArray(mapToList(getInstance(), "contributors",
      contributor -> new JsonObject().put("name", contributor.getString("name"))));
  }

  public String getBarcode() {
    return getProperty(getItem(), "barcode");
  }

  public String getItemId() {
    return getProperty(getItem(), "id");
  }

  public String getHoldingsRecordId() {
    return getProperty(getItem(), "holdingsRecordId");
  }

  public String getInstanceId() {
    return getProperty(getHolding(), "instanceId");
  }

  public String getCallNumber() {
    return getProperty(getHolding(), "callNumber");
  }

  public String getStatus() {
    return getNestedStringProperty(getItem(), "status", "name");
  }

  public JsonObject getLocation() {
    return location;
  }

  public void setLocation(JsonObject location) {
    this.location = location;
  }

  public JsonObject getMaterialType() {
    return materialType;
  }

  public void setMaterialType(JsonObject materialType) {
    this.materialType = materialType;
  }
}
