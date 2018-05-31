package org.folio.circulation.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.representations.ItemProperties;

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

  public boolean isCheckedOut() {
    String status = getStatus();

    return status.equals(ItemStatus.CHECKED_OUT)
      || status.equals(ItemStatus.CHECKED_OUT_HELD)
      || status.equals(ItemStatus.CHECKED_OUT_RECALLED);
  }

  public boolean isNotSameStatus(String prospectiveStatus) {
    return !StringUtils.equals(getStatus(), prospectiveStatus);
  }

  public JsonObject getItem() {
    return item;
  }

  public String getTitle() {
    if(instance != null && instance.containsKey(TITLE_PROPERTY)) {
      return getProperty(instance, TITLE_PROPERTY);
    } else if(getItem() != null) {
      return getProperty(getItem(), TITLE_PROPERTY);
    }
    else {
      return null;
    }
  }

  public JsonArray getContributorNames() {
    if(instance == null) {
      return new JsonArray();
    }

    return new JsonArray(mapToList(instance, "contributors",
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
    return getProperty(holding, "instanceId");
  }

  public String getCallNumber() {
    return getProperty(holding, "callNumber");
  }

  public String getStatus() {
    return getNestedStringProperty(getItem(), "status", "name");
  }

  public JsonObject getLocation() {
    return location;
  }

  public JsonObject getMaterialType() {
    return materialType;
  }

  public void setMaterialType(JsonObject materialType) {
    this.materialType = materialType;
  }

  public String getMaterialTypeId() {
    return getProperty(getItem(), ItemProperties.MATERIAL_TYPE_ID);
  }

  public String getLocationId() {
    return getLocationId(getItem(), holding);
  }

  private static String getLocationId(JsonObject item, JsonObject holding) {
    if(item != null && item.containsKey(ItemProperties.TEMPORARY_LOCATION_ID)) {
      return item.getString(ItemProperties.TEMPORARY_LOCATION_ID);
    }
    else if(holding != null && holding.containsKey(ItemProperties.PERMANENT_LOCATION_ID)) {
      return holding.getString(ItemProperties.PERMANENT_LOCATION_ID);
    }
    else if(item != null && item.containsKey(ItemProperties.PERMANENT_LOCATION_ID)) {
      return item.getString(ItemProperties.PERMANENT_LOCATION_ID);
    }
    else {
      return null;
    }
  }

  public String determineLoanTypeForItem() {
    return getItem().containsKey(ItemProperties.TEMPORARY_LOAN_TYPE_ID)
      && !getItem().getString(ItemProperties.TEMPORARY_LOAN_TYPE_ID).isEmpty()
      ? getItem().getString(ItemProperties.TEMPORARY_LOAN_TYPE_ID)
      : getItem().getString(ItemProperties.PERMANENT_LOAN_TYPE_ID);
  }

  public void changeStatus(String newStatus) {
    getItem().put("status", new JsonObject().put("name", newStatus));
  }

  public boolean isNotFound() {
    return !isFound();
  }

  public boolean isFound() {
    //TODO: Possibly replace with unknown item when migrated
    return getItem() != null;
  }

  public InventoryRecords updateItem(JsonObject updatedItem) {
    return new InventoryRecords(updatedItem,
      holding, instance, getLocation(), getMaterialType());
  }

  public boolean doesNotHaveHolding() {
    return holding == null;
  }

  public InventoryRecords withLocation(JsonObject newLocation) {
    return new InventoryRecords(this.item, this.holding, this.instance,
      newLocation, this.materialType);
  }

  public InventoryRecords withMaterialType(JsonObject newMaterialType) {
    return new InventoryRecords(this.item, this.holding, this.instance,
      this.location, newMaterialType);
  }
}
