package org.folio.circulation.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class InventoryRecords {
  private static final String TITLE_PROPERTY = "title";

  public final JsonObject item;
  public final JsonObject holding;
  public final JsonObject instance;

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
    JsonArray contributors = new JsonArray();

    if(getInstance() != null && getInstance().containsKey("contributors")) {
      JsonArray instanceContributors = getInstance().getJsonArray("contributors");
      if(instanceContributors != null && !instanceContributors.isEmpty()) {
        for(Object ob : instanceContributors) {
          String name = ((JsonObject)ob).getString("name");
          contributors.add(new JsonObject().put("name", name));
        }
      }
    }
    return contributors;
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
}
