package org.folio.circulation.domain;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.domain.ItemStatus.MISSING;
import static org.folio.circulation.domain.representations.InstanceProperties.CONTRIBUTORS;
import static org.folio.circulation.domain.representations.ItemProperties.IN_TRANSIT_DESTINATION_SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.ItemProperties.ITEM_CALL_NUMBER_ID;
import static org.folio.circulation.domain.representations.ItemProperties.ITEM_CALL_NUMBER_PREFIX_ID;
import static org.folio.circulation.domain.representations.ItemProperties.ITEM_CALL_NUMBER_SUFFIX_ID;
import static org.folio.circulation.domain.representations.ItemProperties.ITEM_COPY_NUMBERS_ID;
import static org.folio.circulation.domain.representations.ItemProperties.PERMANENT_LOCATION_ID;
import static org.folio.circulation.domain.representations.ItemProperties.TEMPORARY_LOCATION_ID;
import static org.folio.circulation.domain.representations.ItemProperties.TITLE_PROPERTY;
import static org.folio.circulation.domain.representations.HoldingsProperties.CALL_NUMBER_ID;
import static org.folio.circulation.domain.representations.HoldingsProperties.CALL_NUMBER_PREFIX_ID;
import static org.folio.circulation.domain.representations.HoldingsProperties.CALL_NUMBER_SUFFIX_ID;
import static org.folio.circulation.domain.representations.HoldingsProperties.COPY_NUMBER_ID;
import static org.folio.circulation.support.JsonArrayHelper.mapToList;
import static org.folio.circulation.support.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.remove;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.JsonStringArrayHelper.toStream;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.representations.ItemProperties;
import org.folio.circulation.support.JsonArrayHelper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Item {

  private final JsonObject itemRepresentation;
  private final JsonObject holdingRepresentation;
  private final JsonObject instanceRepresentation;
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
    JsonObject loanTypeRepresentation) {

    this.itemRepresentation = itemRepresentation;
    this.holdingRepresentation = holdingRepresentation;
    this.instanceRepresentation = instanceRepresentation;
    this.location = location;
    this.materialTypeRepresentation = materialTypeRepresentation;
    this.primaryServicePoint = servicePoint;
    this.loanTypeRepresentation = loanTypeRepresentation;
  }

  public static Item from(JsonObject representation) {
    return new Item(representation, null, null, null, null, null, null);
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

  private boolean isInStatus(ItemStatus status) {
    return getStatus().equals(status);
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
    return getEffectiveCallNumberProperty(CALL_NUMBER_ID);
  }

  public String getCallNumberPrefix() {
    return getEffectiveCallNumberProperty(CALL_NUMBER_PREFIX_ID);
  }

  public String getCallNumberSuffix() {
    return getEffectiveCallNumberProperty(CALL_NUMBER_SUFFIX_ID);
  }

  private String getEffectiveCallNumberProperty(String propertyName) {
    return hasItemRepresentationCallNumber()
      ? getProperty(itemRepresentation, mapToItemCallNumberPropertyName(propertyName))
      : getProperty(holdingRepresentation, propertyName);
  }

  private boolean hasItemRepresentationCallNumber() {
    return StringUtils.isNotBlank(getProperty(itemRepresentation, ITEM_CALL_NUMBER_ID));
  }

  private String mapToItemCallNumberPropertyName(String holdingsPropertyName) {
    return Stream.of(ITEM_CALL_NUMBER_ID, ITEM_CALL_NUMBER_PREFIX_ID, ITEM_CALL_NUMBER_SUFFIX_ID)
      .filter(val -> StringUtils.containsIgnoreCase(val, holdingsPropertyName))
      .findFirst()
      .orElse(StringUtils.EMPTY);
  }

  public ItemStatus getStatus() {
    return ItemStatus.from(getStatusName());
  }

  private String getStatusName() {
    return getNestedStringProperty(getItem(), "status", "name");
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

  public JsonArray getCopyNumbers() {
    return getEffectiveCopyNumbers(getArrayProperty(getItem(), ITEM_COPY_NUMBERS_ID));
  }

  private JsonArray getEffectiveCopyNumbers(JsonArray copyNumbers) {
    if (copyNumbers == null || copyNumbers.isEmpty()) {
      return new JsonArray().add(getProperty(holdingRepresentation, COPY_NUMBER_ID));
    }
    return copyNumbers;
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
    write(itemRepresentation, "status",
      new JsonObject().put("name", newStatus.getValue()));

    changed = true;

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

  public Item withLocation(Location newLocation) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      newLocation,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      this.loanTypeRepresentation);
  }

  public Item withMaterialType(JsonObject newMaterialType) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      this.location,
      newMaterialType,
      this.primaryServicePoint,
      this.loanTypeRepresentation);
  }

  public Item withHoldingsRecord(JsonObject newHoldingsRecordRepresentation) {
    return new Item(
      this.itemRepresentation,
      newHoldingsRecordRepresentation,
      this.instanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      this.loanTypeRepresentation);
  }

  public Item withInstance(JsonObject newInstanceRepresentation) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      newInstanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      this.loanTypeRepresentation);
  }

  public Item withPrimaryServicePoint(ServicePoint servicePoint) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      servicePoint,
      this.loanTypeRepresentation);
  }

  public Item withLoanType(JsonObject newLoanTypeRepresentation) {
    return new Item(
      this.itemRepresentation,
      this.holdingRepresentation,
      this.instanceRepresentation,
      this.location,
      this.materialTypeRepresentation,
      this.primaryServicePoint,
      newLoanTypeRepresentation);
  }
}
