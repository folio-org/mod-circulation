package org.folio.circulation.domain;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.domain.representations.ItemProperties.IN_TRANSIT_DESTINATION_SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.ItemProperties.PERMANENT_LOCATION_ID;
import static org.folio.circulation.domain.representations.ItemProperties.TEMPORARY_LOCATION_ID;
import static org.folio.circulation.domain.representations.ItemProperties.TITLE_PROPERTY;
import static org.folio.circulation.support.JsonArrayHelper.mapToList;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.JsonPropertyWriter.remove;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.Objects;
import java.util.UUID;

import org.folio.circulation.domain.representations.ItemProperties;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Item {
  private final JsonObject itemRepresentation;
  private final JsonObject holdingRepresentation;
  private final JsonObject instanceRepresentation;
  private JsonObject locationRepresentation;
  private JsonObject materialTypeRepresentation;

  private boolean changed = false;

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

  public boolean hasChanged() {
    return changed;
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

  public ItemStatus getStatus() {
    return ItemStatus.from(getStatusName());
  }

  private String getStatusName() {
    return getNestedStringProperty(getItem(), "status", "name");
  }

  public JsonObject getLocation() {
    return locationRepresentation;
  }

  boolean matchesPrimaryServicePoint(UUID servicePointId) {
    final UUID primaryServicePointId = getPrimaryServicePointId();

    return Objects.equals(primaryServicePointId, servicePointId);
  }

  private UUID getPrimaryServicePointId() {
    return getUUIDProperty(getLocation(), "primaryServicePoint");
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

  String getEnumeration() {
    return getProperty(getItem(), "enumeration");
  }

  public String getInTransitDestinationServicePointId() {
    return getProperty(itemRepresentation, IN_TRANSIT_DESTINATION_SERVICE_POINT_ID);
  }

  public String determineLoanTypeForItem() {
    return getItem().containsKey(ItemProperties.TEMPORARY_LOAN_TYPE_ID)
      && !getItem().getString(ItemProperties.TEMPORARY_LOAN_TYPE_ID).isEmpty()
      ? getItem().getString(ItemProperties.TEMPORARY_LOAN_TYPE_ID)
      : getItem().getString(ItemProperties.PERMANENT_LOAN_TYPE_ID);
  }

  Item changeStatus(ItemStatus newStatus) {
    //TODO: Check if status is null
    write(itemRepresentation, "status",
      new JsonObject().put("name", newStatus.getValue()));

    changed = true;

    return this;
  }

  Item available() {
    return changeStatus(AVAILABLE)
      .removeDestination();
  }

  Item inTransitToHome() {
    return changeStatus(IN_TRANSIT)
      .changeDestination(getPrimaryServicePointId());
  }

  private Item changeDestination(UUID destinationServicePointId) {
    write(itemRepresentation, IN_TRANSIT_DESTINATION_SERVICE_POINT_ID,
      destinationServicePointId);

    return this;
  }

  private Item removeDestination() {
    remove(itemRepresentation, IN_TRANSIT_DESTINATION_SERVICE_POINT_ID);

    return this;
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
