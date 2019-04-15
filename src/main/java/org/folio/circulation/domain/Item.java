package org.folio.circulation.domain;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.domain.ItemStatus.MISSING;
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
import static org.folio.circulation.support.JsonStringArrayHelper.toStream;

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
  private ServicePoint primaryServicePoint;
  private ServicePoint inTransitDestinationServicePoint;

  private boolean changed = false;

  public Item(
    JsonObject itemRepresentation,
    JsonObject holdingRepresentation,
    JsonObject instanceRepresentation,
    JsonObject locationRepresentation,
    JsonObject materialTypeRepresentation,
    ServicePoint servicePoint) {

    this.itemRepresentation = itemRepresentation;
    this.holdingRepresentation = holdingRepresentation;
    this.instanceRepresentation = instanceRepresentation;
    this.locationRepresentation = locationRepresentation;
    this.materialTypeRepresentation = materialTypeRepresentation;
    this.primaryServicePoint = servicePoint;
  }

  public static Item from(JsonObject representation) {
    return new Item(representation, null, null, null, null, null);
  }

  public boolean isCheckedOut() {
    return isStatus(CHECKED_OUT);
  }

  public boolean isMissing() {
    return isStatus(MISSING);
  }

  public boolean isAwaitingPickup() {
    return isStatus(AWAITING_PICKUP);
  }

  private boolean isInTransitPickup() {
    return isStatus(IN_TRANSIT);
  }

  private boolean isStatus(ItemStatus inTransit) {
    return getStatus().equals(inTransit);
  }


  Boolean isNotSameStatus(ItemStatus prospectiveStatus) {
    return !Objects.equals(getStatus(), prospectiveStatus);
  }

  boolean hasChanged() {
    return changed;
  }

  public JsonObject getItem() {
    return itemRepresentation;
  }

  public String getTitle() {
    if(instanceRepresentation != null
      && instanceRepresentation.containsKey(TITLE_PROPERTY)) {

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

  boolean homeLocationIsServedBy(UUID servicePointId) {
    //Defensive check just in case primary isn't part of serving set
    return matchesPrimaryServicePoint(servicePointId) ||
      matchesAnyServingServicePoint(servicePointId);
  }

  private boolean matchesPrimaryServicePoint(UUID servicePointId) {
    return matchingId(getPrimaryServicePointId(), servicePointId);
  }

  private boolean matchesAnyServingServicePoint(UUID servicePointId) {
    return toStream(locationRepresentation, "servicePointIds")
      .map(UUID::fromString)
      .anyMatch(servingServicePointId ->
        matchingId(servicePointId, servingServicePointId));
  }

  private boolean matchingId(UUID first, UUID second) {
    return Objects.equals(second, first);
  }

  public UUID getPrimaryServicePointId() {
    return getUUIDProperty(getLocation(), "primaryServicePoint");
  }

  public JsonObject getMaterialType() {
    return materialTypeRepresentation;
  }

  public JsonArray getCopyNumbers() {
    return getItem() == null ? new JsonArray() : getItem().getJsonArray("copyNumbers");
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

  public ServicePoint getInTransitDestinationServicePoint() {
    if(getInTransitDestinationServicePointId() == null) {
      return null;
    }

    return inTransitDestinationServicePoint;
  }

  private ServicePoint getPrimaryServicePoint() {
    return primaryServicePoint;
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

    //TODO: Remove this hack to remove destination service point
    // needs refactoring of how in transit for pickup is done
    if(!isInTransitPickup()) {
      return removeDestination();
    }
    else {
      return this;
    }
  }

  Item available() {
    return changeStatus(AVAILABLE)
      .removeDestination();
  }

  Item inTransitToHome() {
    return changeStatus(IN_TRANSIT)
      .changeDestination(getPrimaryServicePointId())
      .changeInTransitDestinationServicePoint(getPrimaryServicePoint());
  }

  Item inTransitToServicePoint(UUID destinationServicePointId) {
    return changeStatus(IN_TRANSIT)
      .changeDestination(destinationServicePointId);
  }

  Item updateDestinationServicePoint(ServicePoint servicePoint) {
    return changeInTransitDestinationServicePoint(servicePoint);
  }

  private Item changeDestination(UUID destinationServicePointId) {
    write(itemRepresentation, IN_TRANSIT_DESTINATION_SERVICE_POINT_ID,
      destinationServicePointId);

    return this;
  }

  private Item removeDestination() {
    remove(itemRepresentation, IN_TRANSIT_DESTINATION_SERVICE_POINT_ID);

    this.inTransitDestinationServicePoint = null;

    return this;
  }

  private Item changeInTransitDestinationServicePoint(ServicePoint inTransitDestinationServicePoint) {
    this.inTransitDestinationServicePoint = inTransitDestinationServicePoint;

    return this;
  }

  public boolean isNotFound() {
    return !isFound();
  }

  public boolean isFound() {
    //TODO: Possibly replace with unknown item when migrated
    return getItem() != null;
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
      this.materialTypeRepresentation,
      this.primaryServicePoint);
  }

  public Item withMaterialType(JsonObject newMaterialType) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      this.locationRepresentation,
      newMaterialType,
      this.primaryServicePoint);
  }

  public Item withHoldingsRecord(JsonObject newHoldingsRecordRepresentation) {
    return new Item(
      this.itemRepresentation,
      newHoldingsRecordRepresentation,
      this.instanceRepresentation,
      this.locationRepresentation,
      this.materialTypeRepresentation,
      this.primaryServicePoint);
  }

  public Item withInstance(JsonObject newInstanceRepresentation) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      newInstanceRepresentation,
      this.locationRepresentation,
      this.materialTypeRepresentation,
      this.primaryServicePoint);
  }

  public Item withPrimaryServicePoint(ServicePoint servicePoint) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      this.locationRepresentation,
      this.materialTypeRepresentation,
      servicePoint);
  }
}
