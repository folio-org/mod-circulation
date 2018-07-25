package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.representations.ItemProperties;

import java.util.Objects;

import static org.folio.circulation.domain.representations.ItemProperties.*;
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

  public static Item from(JsonObject representation) {
    return new Item(representation, null, null, null, null);
  }

  public boolean isCheckedOut() {
    return getStatus().equals(ItemStatus.CHECKED_OUT);
  }

  Boolean isNotSameStatus(ItemStatus prospectiveStatus) {
    return !Objects.equals(getStatus(), prospectiveStatus);
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

  JsonArray getContributorNames() {
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

  String getCallNumber() {
    return getProperty(holdingRepresentation, "callNumber");
  }

  ItemStatus getStatus() {
    return ItemStatus.from(getStatusName());
  }

  private String getStatusName() {
    return getNestedStringProperty(getItem(), "status", "name");
  }

  public JsonObject getLocation() {
    return locationRepresentation;
  }

  JsonObject getMaterialType() {
    return materialTypeRepresentation;
  }

  public String getMaterialTypeId() {
    return getProperty(getItem(), ItemProperties.MATERIAL_TYPE_ID);
  }

  public String getLocationId() {
    return getLocationId(getItem(), holdingRepresentation);
  }

  private static String getLocationId(JsonObject item, JsonObject holding) {
    if(item != null && item.containsKey(TEMPORARY_LOCATION_ID)) {
      return item.getString(TEMPORARY_LOCATION_ID);
    }
    if(item != null && item.containsKey(PERMANENT_LOCATION_ID)) {
      return item.getString(PERMANENT_LOCATION_ID);
    }
    else if(holding != null && holding.containsKey(TEMPORARY_LOCATION_ID)) {
      return holding.getString(TEMPORARY_LOCATION_ID);
    }
    else if(holding != null && holding.containsKey(PERMANENT_LOCATION_ID)) {
      return holding.getString(PERMANENT_LOCATION_ID);
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

  void changeStatus(ItemStatus newStatus) {
    //TODO: Check if status is null
    getItem().put("status", new JsonObject().put("name", newStatus.getValue()));
  }

  public boolean isNotFound() {
    return !isFound();
  }

  public boolean isFound() {
    //TODO: Possibly replace with unknown item when migrated
    return getItem() != null;
  }

  Item updateItem(JsonObject updatedItem) {
    return new Item(updatedItem,
      holdingRepresentation,
      instanceRepresentation,
      getLocation(),
      getMaterialType());
  }

  public boolean doesNotHaveHolding() {
    return holdingRepresentation == null;
  }

  public Item withLocation(JsonObject newLocation) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      newLocation,
      this.materialTypeRepresentation);
  }

  public Item withMaterialType(JsonObject newMaterialType) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      this.locationRepresentation,
      newMaterialType);
  }

  public Item withHoldingsRecord(JsonObject newHoldingsRecordRepresentation) {
    return new Item(
      this.itemRepresentation,
      newHoldingsRecordRepresentation,
      this.instanceRepresentation,
      this.locationRepresentation,
      this.materialTypeRepresentation);
  }

  public Item withInstance(JsonObject newInstanceRepresentation) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      newInstanceRepresentation,
      this.locationRepresentation,
      this.materialTypeRepresentation);
  }
}
