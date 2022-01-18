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
import static org.folio.circulation.domain.representations.ItemProperties.EFFECTIVE_LOCATION_ID;
import static org.folio.circulation.domain.representations.ItemProperties.IN_TRANSIT_DESTINATION_SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.ItemProperties.ITEM_COPY_NUMBER_ID;
import static org.folio.circulation.domain.representations.ItemProperties.LAST_CHECK_IN;
import static org.folio.circulation.domain.representations.ItemProperties.MATERIAL_TYPE_ID;
import static org.folio.circulation.domain.representations.ItemProperties.PERMANENT_LOAN_TYPE_ID;
import static org.folio.circulation.domain.representations.ItemProperties.PERMANENT_LOCATION_ID;
import static org.folio.circulation.domain.representations.ItemProperties.STATUS_PROPERTY;
import static org.folio.circulation.domain.representations.ItemProperties.TEMPORARY_LOAN_TYPE_ID;
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

import org.folio.circulation.storage.mappers.ItemMapper;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public class Item {
  private final JsonObject itemRepresentation;
  private final Location location;
  private final LastCheckIn lastCheckIn;
  private final CallNumberComponents callNumberComponents;
  private final Location permanentLocation;

  private ServicePoint inTransitDestinationServicePoint;
  private boolean changed;

  @NonNull private final Holdings holdings;
  @NonNull private final Instance instance;
  @NonNull private final MaterialType materialType;
  @NonNull private final LoanType loanType;

  public static Item from(JsonObject representation) {
    return new ItemMapper().toDomain(representation);
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

  public String getTitle() {
    return instance.getTitle();
  }

  public Stream<String> getContributorNames() {
    return instance.getContributorNames();
  }

  public String getPrimaryContributorName() {
    return instance.getPrimaryContributorName();
  }

  public Stream<Identifier> getIdentifiers() {
    return instance.getIdentifiers().stream();
  }

  public String getBarcode() {
    return getProperty(itemRepresentation, "barcode");
  }

  public String getItemId() {
    return getProperty(itemRepresentation, "id");
  }

  public String getHoldingsRecordId() {
    return getProperty(itemRepresentation, "holdingsRecordId");
  }

  public String getInstanceId() {
    return holdings.getInstanceId();
  }

  public Stream<Publication> getPublication() {
    return instance.getPublication().stream();
  }

  public Stream<String> getEditions() {
    return instance.getEditions().stream();
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
    return getNestedStringProperty(itemRepresentation, STATUS_PROPERTY, "name");
  }

  private String getStatusDate() {
    return getNestedStringProperty(itemRepresentation, STATUS_PROPERTY, "date");
  }

  public Location getLocation() {
    return location;
  }

  public Location getPermanentLocation() {
    return permanentLocation;
  }

  public MaterialType getMaterialType() {
    return materialType;
  }

  public String getMaterialTypeName() {
    return materialType.getName();
  }

  public String getCopyNumber() {
    return firstNonBlank(
      getProperty(itemRepresentation, ITEM_COPY_NUMBER_ID),
      holdings.getCopyNumber());
  }

  public String getMaterialTypeId() {
    return getProperty(itemRepresentation, MATERIAL_TYPE_ID);
  }

  public String getLocationId() {
    return getProperty(itemRepresentation, EFFECTIVE_LOCATION_ID);
  }

  public String getEnumeration() {
    return getProperty(itemRepresentation, "enumeration");
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
    return getProperty(itemRepresentation, "volume");
  }

  public String getChronology() {
    return getProperty(itemRepresentation, "chronology");
  }

  public String getNumberOfPieces() {
    return getProperty(itemRepresentation, "numberOfPieces");
  }

  public String getDescriptionOfPieces() {
    return getProperty(itemRepresentation, "descriptionOfPieces");
  }

  public List<String> getYearCaption() {
    return toStream(itemRepresentation, "yearCaption")
      .collect(Collectors.toList());
  }

  private ServicePoint getPrimaryServicePoint() {
    if (location == null) {
      return null;
    }

    return location.getPrimaryServicePoint();
  }

  public String getLoanTypeId() {
    return firstNonBlank(getProperty(itemRepresentation, TEMPORARY_LOAN_TYPE_ID),
      getProperty(itemRepresentation, PERMANENT_LOAN_TYPE_ID));
  }

  public String getLoanTypeName() {
    return loanType.getName();
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
    return itemRepresentation != null;
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
      newLocation,
      this.lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings, this.instance, this.materialType, loanType);
  }

  public Item withMaterialType(MaterialType materialType) {
    return new Item(
      this.itemRepresentation,
      this.location,
      this.lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings, this.instance,
      materialType, loanType);
  }

  public Item withHoldings(@NonNull Holdings holdings) {
    return new Item(
      this.itemRepresentation,
      this.location,
      this.lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed,
      holdings, this.instance, this.materialType, loanType);
  }

  public Item withInstance(@NonNull Instance instance) {
    return new Item(
      this.itemRepresentation,
      this.location,
      this.lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings,
      instance, this.materialType, loanType);
  }

  public Item withLoanType(LoanType loanType) {
    return new Item(
      this.itemRepresentation,
      this.location,
      this.lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings, this.instance, this.materialType,
      loanType);
  }

  public Item withLastCheckIn(@NonNull LastCheckIn lastCheckIn) {
    final var changedItemRepresentation = itemRepresentation.copy();

    write(changedItemRepresentation, LAST_CHECK_IN, lastCheckIn.toJson());

    return new Item(
      changedItemRepresentation,
      this.location,
      lastCheckIn,
      this.callNumberComponents,
      this.permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings, this.instance, this.materialType, loanType);
  }

  public Item withPermanentLocation(Location permanentLocation) {
    return new Item(
      this.itemRepresentation,
      this.location,
      this.lastCheckIn,
      this.callNumberComponents,
      permanentLocation,
      this.inTransitDestinationServicePoint,
      this.changed, holdings, this.instance, this.materialType, loanType);
  }
}
