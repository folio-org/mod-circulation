package org.folio.circulation.domain;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.domain.ItemStatus.MISSING;
import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.domain.representations.HoldingsProperties.COPY_NUMBER_ID;
import static org.folio.circulation.domain.representations.InstanceProperties.CONTRIBUTORS;
import static org.folio.circulation.domain.representations.ItemProperties.EFFECTIVE_LOCATION_ID;
import static org.folio.circulation.domain.representations.ItemProperties.IN_TRANSIT_DESTINATION_SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.ItemProperties.ITEM_COPY_NUMBER_ID;
import static org.folio.circulation.domain.representations.ItemProperties.TITLE_PROPERTY;
import static org.folio.circulation.support.JsonArrayHelper.mapToList;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.remove;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.JsonStringArrayHelper.toStream;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.representations.ItemProperties;
import org.folio.circulation.support.JsonArrayHelper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Item {

  private final JsonObject itemRepresentation;
  private final JsonObject holdingRepresentation;
  private final JsonObject instanceRepresentation;
  private final LastCheckIn lastCheckIn;
  private final CallNumberComponents callNumberComponents;

  private Location location;
  private JsonObject materialTypeRepresentation;
  private ServicePoint primaryServicePoint;
  private ServicePoint inTransitDestinationServicePoint;
  private JsonObject loanTypeRepresentation;

  private boolean changed = false;

  public Item(
    JsonObject itemRepresentation,
    JsonObject holdingRepresentation,
    JsonObject instanceRepresentation,
    Location location,
    JsonObject materialTypeRepresentation,
    ServicePoint servicePoint,
    JsonObject loanTypeRepresentation,
    LastCheckIn lastCheckIn,
    CallNumberComponents callNumberComponents) {

    this.itemRepresentation = itemRepresentation;
    this.holdingRepresentation = holdingRepresentation;
    this.instanceRepresentation = instanceRepresentation;
    this.location = location;
    this.materialTypeRepresentation = materialTypeRepresentation;
    this.primaryServicePoint = servicePoint;
    this.loanTypeRepresentation = loanTypeRepresentation;
    this.lastCheckIn = lastCheckIn;
    this.callNumberComponents = callNumberComponents;
  }

  public static Item from(JsonObject representation) {
    return new Item(representation,
      null,
      null,
      null,
      null,
      null,
      null,
      LastCheckIn.fromItemJson(representation),
      CallNumberComponents.fromItemJson(representation)
    );
  }

  public boolean isCheckedOut() {
    return isInStatus(CHECKED_OUT);
  }

  public boolean isPaged() {
    return isInStatus(PAGED);
  }

  public boolean isMissing() {
    return isInStatus(MISSING);
  }

  public boolean isAwaitingPickup() {
    return isInStatus(AWAITING_PICKUP);
  }

  public boolean isAvailable() {
    return isInStatus(AVAILABLE);
  }

  private boolean isInTransit() {
    return isInStatus(IN_TRANSIT);
  }

  boolean isNotSameStatus(ItemStatus prospectiveStatus) {
    return !isInStatus(prospectiveStatus);
  }

  public boolean isInStatus(ItemStatus status) {
    return getStatus().equals(status);
  }

  public boolean hasChanged() {
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

    return new JsonArray(mapToList(instanceRepresentation, CONTRIBUTORS,
      contributor -> new JsonObject().put("name", contributor.getString("name"))));
  }

  public String getPrimaryContributorName() {
    return JsonArrayHelper.toStream(instanceRepresentation, CONTRIBUTORS)
      .filter(c -> c.getBoolean("primary", false))
      .findFirst()
      .map(c -> c.getString("name"))
      .orElse(null);
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
    return Optional.ofNullable(callNumberComponents)
      .map(CallNumberComponents::getCallNumber)
      .orElse(null);
  }

  public UUID getLastCheckInServicePointId() {
    return Optional.ofNullable(lastCheckIn)
      .map(LastCheckIn::getServicePointId)
      .orElse(null);
  }

  public CallNumberComponents getCallNumberComponents() {
    return callNumberComponents;
  }

  public ItemStatus getStatus() {
    return ItemStatus.from(getStatusName(), getStatusDate());
  }

  public String getStatusName() {
    return getNestedStringProperty(getItem(), ItemProperties.STATUS_PROPERTY, "name");
  }

  private String getStatusDate() {
    return getNestedStringProperty(getItem(), ItemProperties.STATUS_PROPERTY, "date");
  }

  public Location getLocation() {
    return location;
  }

  public JsonObject getMaterialType() {
    return materialTypeRepresentation;
  }

  public String getMaterialTypeName() {
    return getProperty(materialTypeRepresentation, "name");
  }

  public String getCopyNumber() {
    return StringUtils.firstNonBlank(
      getProperty(getItem(), ITEM_COPY_NUMBER_ID),
      getProperty(holdingRepresentation, COPY_NUMBER_ID)
    );
  }

  public String getMaterialTypeId() {
    return getProperty(getItem(), ItemProperties.MATERIAL_TYPE_ID);
  }

  public String getLocationId() {
    return getProperty(getItem(), EFFECTIVE_LOCATION_ID);
  }

  public String getEnumeration() {
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

  public String getVolume() {
    return getProperty(getItem(), "volume");
  }

  public String getChronology() {
    return getProperty(getItem(), "chronology");
  }

  public String getNumberOfPieces() {
    return getProperty(getItem(), "numberOfPieces");
  }

  public String getDescriptionOfPieces() {
    return getProperty(getItem(), "descriptionOfPieces");
  }

  public List<String> getYearCaption() {
    return toStream(itemRepresentation, "yearCaption")
      .collect(Collectors.toList());
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

  public String getLoanTypeName() {
    return getProperty(loanTypeRepresentation, "name");
  }


  Item changeStatus(ItemStatus newStatus) {
    //TODO: Check if status is null

    if (isNotSameStatus(newStatus)) {
      changed = true;
    }

    write(itemRepresentation, ItemProperties.STATUS_PROPERTY,
      new JsonObject().put("name", newStatus.getValue()));

    //TODO: Remove this hack to remove destination service point
    // needs refactoring of how in transit for pickup is done
    if(!isInTransit()) {
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
      .changeDestination(location.getPrimaryServicePointId())
      .changeInTransitDestinationServicePoint(getPrimaryServicePoint());
  }

  Item inTransitToServicePoint(UUID destinationServicePointId) {
    return changeStatus(IN_TRANSIT)
      .changeDestination(destinationServicePointId);
  }

  public Item updateDestinationServicePoint(ServicePoint servicePoint) {
    return changeInTransitDestinationServicePoint(servicePoint);
  }

  public Item updateLastCheckInServicePoint(ServicePoint servicePoint) {
    if (lastCheckIn != null) {
      lastCheckIn.setServicePoint(servicePoint);
    }
    return this;
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

  public LastCheckIn getLastCheckIn() {
    return lastCheckIn;
  }

  public boolean doesNotHaveHolding() {
    return holdingRepresentation == null;
  }

  public Item withLocation(Location newLocation) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      newLocation,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      this.loanTypeRepresentation,
      lastCheckIn,
      this.callNumberComponents);
  }

  public Item withMaterialType(JsonObject newMaterialType) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      this.location,
      newMaterialType,
      this.primaryServicePoint,
      this.loanTypeRepresentation,
      lastCheckIn,
      this.callNumberComponents);
  }

  public Item withHoldingsRecord(JsonObject newHoldingsRecordRepresentation) {
    return new Item(
      this.itemRepresentation,
      newHoldingsRecordRepresentation,
      this.instanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      this.loanTypeRepresentation,
      lastCheckIn,
      this.callNumberComponents);
  }

  public Item withInstance(JsonObject newInstanceRepresentation) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      newInstanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      this.loanTypeRepresentation,
      lastCheckIn,
      this.callNumberComponents);
  }

  public Item withPrimaryServicePoint(ServicePoint servicePoint) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      servicePoint,
      this.loanTypeRepresentation,
      lastCheckIn,
      this.callNumberComponents);
  }

  public Item withLoanType(JsonObject newLoanTypeRepresentation) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      newLoanTypeRepresentation,
      lastCheckIn,
      this.callNumberComponents);
  }

  public Item withLastCheckIn(LastCheckIn lastCheckIn) {
    Item item = new Item(
      itemRepresentation,
      holdingRepresentation,
      instanceRepresentation,
      location,
      materialTypeRepresentation,
      primaryServicePoint,
      loanTypeRepresentation,
      lastCheckIn,
      this.callNumberComponents);
    item.changed = this.changed;
    return item;
  }
}
