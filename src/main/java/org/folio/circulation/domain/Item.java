package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.domain.ItemStatus.MISSING;
import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.domain.representations.InstanceProperties.CONTRIBUTORS;
import static org.folio.circulation.domain.representations.InstanceProperties.EDITIONS;
import static org.folio.circulation.domain.representations.InstanceProperties.PUBLICATION;
import static org.folio.circulation.domain.representations.ItemProperties.EFFECTIVE_LOCATION_ID;
import static org.folio.circulation.domain.representations.ItemProperties.IDENTIFIERS;
import static org.folio.circulation.domain.representations.ItemProperties.IN_TRANSIT_DESTINATION_SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.ItemProperties.ITEM_COPY_NUMBER_ID;
import static org.folio.circulation.domain.representations.ItemProperties.PERMANENT_LOCATION_ID;
import static org.folio.circulation.domain.representations.ItemProperties.STATUS_PROPERTY;
import static org.folio.circulation.domain.representations.ItemProperties.TITLE;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.remove;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonStringArrayPropertyFetcher.toStream;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.circulation.domain.representations.ItemProperties;
import org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public class Item {
  private final JsonObject itemRepresentation;
  private final JsonObject instanceRepresentation;
  private final Location location;
  private final JsonObject materialTypeRepresentation;
  private final ServicePoint primaryServicePoint;
  private final JsonObject loanTypeRepresentation;
  private final LastCheckIn lastCheckIn;
  private final CallNumberComponents callNumberComponents;
  private final Location permanentLocation;

  private ServicePoint inTransitDestinationServicePoint;
  private boolean changed;

  private final Holdings holdings;

  public static Item from(JsonObject representation) {
    return new Item(representation,
      null,
      null,
      null,
      null,
      null,
      LastCheckIn.fromItemJson(representation),
      CallNumberComponents.fromItemJson(representation),
      null,
      null,
      false,
      Holdings.unknown());
  }

  public boolean isCheckedOut() {
    return isInStatus(CHECKED_OUT);
  }

  public boolean isClaimedReturned() {
    return isInStatus(CLAIMED_RETURNED);
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

  public boolean isDeclaredLost() {
    return isInStatus(DECLARED_LOST);
  }

  boolean isNotSameStatus(ItemStatus prospectiveStatus) {
    return !isInStatus(prospectiveStatus);
  }

  public boolean isInStatus(ItemStatus status) {
    return getStatus().equals(status);
  }

  public boolean isNotInStatus(ItemStatus status) {
    return !isInStatus(status);
  }

  public boolean hasChanged() {
    return changed;
  }

  public JsonObject getItem() {
    return itemRepresentation;
  }

  public String getTitle() {
    return getProperty(instanceRepresentation, TITLE);
  }

  public JsonArray getIdentifiers() {
    return getArrayProperty(instanceRepresentation, IDENTIFIERS);
  }

  public String getPrimaryContributorName() {
    return getContributors()
      .filter(c -> c.getBoolean("primary", false))
      .findFirst()
      .map(c -> c.getString("name"))
      .orElse(null);
  }

  public Stream<JsonObject> getContributors() {
    return JsonObjectArrayPropertyFetcher.toStream(instanceRepresentation, CONTRIBUTORS);
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
    return holdings.getInstanceId();
  }

  public JsonArray getPublication() {
    return getArrayProperty(instanceRepresentation, PUBLICATION);
  }

  public JsonArray getEditions() {
    return getArrayProperty(instanceRepresentation, EDITIONS);
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
    return getNestedStringProperty(getItem(), STATUS_PROPERTY, "name");
  }

  private String getStatusDate() {
    return getNestedStringProperty(getItem(), STATUS_PROPERTY, "date");
  }

  public Location getLocation() {
    return location;
  }

  public Location getPermanentLocation() {
    return permanentLocation;
  }

  public JsonObject getMaterialType() {
    return materialTypeRepresentation;
  }

  public String getMaterialTypeName() {
    return getProperty(materialTypeRepresentation, "name");
  }

  public String getCopyNumber() {
    return firstNonBlank(
      getProperty(getItem(), ITEM_COPY_NUMBER_ID),
      holdings.getCopyNumber());
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


  public Item changeStatus(ItemStatus newStatus) {
    if (isNotSameStatus(newStatus)) {
      changed = true;
    }

    write(itemRepresentation, STATUS_PROPERTY,
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
    return getItem() != null;
  }

  public LastCheckIn getLastCheckIn() {
    return lastCheckIn;
  }

  public String getPermanentLocationId() {
    final String itemLocation = getProperty(itemRepresentation, PERMANENT_LOCATION_ID);

    return firstNonBlank(itemLocation, holdings.getPermanentLocationId());
  }

  public Item withLocation(Location newLocation) {
    return new Item(
      this.itemRepresentation,
      this.instanceRepresentation,
      newLocation,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      this.loanTypeRepresentation,
      this.lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings);
  }

  public Item withMaterialType(JsonObject newMaterialType) {
    return new Item(
      this.itemRepresentation,
      this.instanceRepresentation,
      this.location,
      newMaterialType,
      this.primaryServicePoint,
      this.loanTypeRepresentation,
      this.lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings);
  }

  public Item withHoldings(@NonNull Holdings holdings) {
    return new Item(
      this.itemRepresentation,
      this.instanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      this.loanTypeRepresentation,
      this.lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed,
      holdings);
  }

  public Item withInstance(JsonObject newInstanceRepresentation) {
    return new Item(
      this.itemRepresentation,
      newInstanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      this.loanTypeRepresentation,
      this.lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings);
  }

  public Item withPrimaryServicePoint(ServicePoint servicePoint) {
    return new Item(
      this.itemRepresentation,
      this.instanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      servicePoint,
      this.loanTypeRepresentation,
      this.lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings);
  }

  public Item withLoanType(JsonObject newLoanTypeRepresentation) {
    return new Item(
      this.itemRepresentation,
      this.instanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      newLoanTypeRepresentation,
      this.lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings);
  }

  public Item withLastCheckIn(LastCheckIn lastCheckIn) {
    return new Item(
      this.itemRepresentation,
      this.instanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      this.loanTypeRepresentation,
      lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings);
  }

  public Item withPermanentLocation(Location permanentLocation) {
    return new Item(
      this.itemRepresentation,
      this.instanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      this.loanTypeRepresentation,
      this.lastCheckIn,
      this.callNumberComponents,
      permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings);
  }
}
