package org.folio.circulation.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.representations.ItemProperties;

import static org.folio.circulation.domain.representations.ItemProperties.TITLE_PROPERTY;
import static org.folio.circulation.support.JsonArrayHelper.mapToList;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class Item {

  private final JsonObject itemRepresentation;
  private final JsonObject holdingRepresentation;
  private final JsonObject instanceRepresentation;
  private JsonObject locationRepresentation;
  private JsonObject materialTypeRepresentation;

  public Item(
    JsonObject itemRepresentation,
    JsonObject holdingRepresentation,
    JsonObject instanceRepresentation,
    JsonObject locationRepresentation,
    JsonObject materialTypeRepresentation) {

    this.itemRepresentation = itemRepresentation;
    this.holdingRepresentation = holdingRepresentation;
    this.instanceRepresentation = instanceRepresentation;
    this.locationRepresentation = locationRepresentation;
    this.materialTypeRepresentation = materialTypeRepresentation;
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
    return itemRepresentation;
  }

  public String getTitle() {
    if(instanceRepresentation != null && instanceRepresentation.containsKey(TITLE_PROPERTY)) {
      return getProperty(instanceRepresentation, TITLE_PROPERTY);
    } else if(getItem() != null) {
      return getProperty(getItem(), TITLE_PROPERTY);
    }
    else {
      return null;
    }
  }

  public JsonArray getContributorNames() {
    if(instanceRepresentation == null) {
      return new JsonArray();
    }

    return new JsonArray(mapToList(instanceRepresentation, "contributors",
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
    return getProperty(holdingRepresentation, "instanceId");
  }

  public String getCallNumber() {
    return getProperty(holdingRepresentation, "callNumber");
  }

  public String getStatus() {
    return getNestedStringProperty(getItem(), "status", "name");
  }

  public JsonObject getLocation() {
    return locationRepresentation;
  }

  public JsonObject getMaterialType() {
    return materialTypeRepresentation;
  }

  public String getMaterialTypeId() {
    return getProperty(getItem(), ItemProperties.MATERIAL_TYPE_ID);
  }

  public String getLocationId() {
    return getLocationId(getItem(), holdingRepresentation);
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

  public Item updateItem(JsonObject updatedItem) {
    return new Item(updatedItem,
      holdingRepresentation, instanceRepresentation, getLocation(), getMaterialType());
  }

  public boolean doesNotHaveHolding() {
    return holdingRepresentation == null;
  }

  public Item withLocation(JsonObject newLocation) {
    return new Item(this.itemRepresentation, this.holdingRepresentation, this.instanceRepresentation,
      newLocation, this.materialTypeRepresentation);
  }

  public Item withMaterialType(JsonObject newMaterialType) {
    return new Item(this.itemRepresentation, this.holdingRepresentation, this.instanceRepresentation,
      this.locationRepresentation, newMaterialType);
  }
}
