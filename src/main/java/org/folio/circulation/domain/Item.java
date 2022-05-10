package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.folio.circulation.domain.ItemStatusName.AVAILABLE;
import static org.folio.circulation.domain.ItemStatusName.AWAITING_PICKUP;
import static org.folio.circulation.domain.ItemStatusName.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatusName.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatusName.DECLARED_LOST;
import static org.folio.circulation.domain.ItemStatusName.IN_TRANSIT;
import static org.folio.circulation.domain.ItemStatusName.MISSING;
import static org.folio.circulation.domain.ItemStatusName.PAGED;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.folio.circulation.storage.mappers.ItemMapper;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public class Item {
  private final String id;
  @NonNull private final Location location;
  private final LastCheckIn lastCheckIn;
  private final CallNumberComponents callNumberComponents;
  @NonNull private final Location permanentLocation;
  private final ServicePoint inTransitDestinationServicePoint;
  private final boolean changed;
  @NonNull private final Holdings holdings;
  @NonNull private final Instance instance;
  @NonNull private final MaterialType materialType;
  @NonNull private final LoanType loanType;
  @NonNull private final ItemDescription description;
  @NonNull private final ItemStatus status;

  public static Item unknown() {
    return unknown(null);
  }

  public static Item unknown(String id) {
    return new Item(id, Location.unknown(), LastCheckIn.unknown(),
      CallNumberComponents.unknown(), Location.unknown(), ServicePoint.unknown(),
      false, Holdings.unknown(), Instance.unknown(),
      MaterialType.unknown(), LoanType.unknown(), ItemDescription.unknown(),
      ItemStatus.unknown());
  }

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

  public boolean isDeclaredLost() {
    return isInStatus(DECLARED_LOST);
  }

  public boolean isInStatus(ItemStatusName status) {
    return getStatus().is(status);
  }

  public boolean isNotInStatus(ItemStatusName status) {
    return !getStatus().is(status);
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
    return description.getBarcode();
  }

  public String getItemId() {
    return id;
  }

  public String getHoldingsRecordId() {
    return holdings.getId();
  }

  public String getInstanceId() {
    return holdings.getInstanceId();
  }

  public Stream<Publication> getPublication() {
    return instance.getPublication().stream();
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
    return status;
  }

  public String getStatusName() {
    return status.getName().getName();
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
    return firstNonBlank(description.getCopyNumber(), holdings.getCopyNumber());
  }

  public String getMaterialTypeId() {
    return materialType.getId();
  }

  public String getEffectiveLocationId() {
    return location.getId();
  }

  public String getEnumeration() {
    return description.getEnumeration();
  }

  public String getInTransitDestinationServicePointId() {
    if (inTransitDestinationServicePoint == null) {
      return null;
    }
    else {
      return inTransitDestinationServicePoint.getId();
    }
  }

  public ServicePoint getInTransitDestinationServicePoint() {
    return inTransitDestinationServicePoint;
  }

  public String getVolume() {
    return description.getVolume();
  }

  public String getChronology() {
    return description.getChronology();
  }

  public String getNumberOfPieces() {
    return description.getNumberOfPieces();
  }

  public String getDescriptionOfPieces() {
    return description.getDescriptionOfPieces();
  }

  public Collection<String> getYearCaption() {
    return description.getYearCaption();
  }

  private ServicePoint getPrimaryServicePoint() {
    return location.getPrimaryServicePoint();
  }

  public String getLoanTypeId() {
    return loanType.getId();
  }

  public String getLoanTypeName() {
    return loanType.getName();
  }

  public String getPermanentLocationName() {
    if (getPermanentLocation() != null && getPermanentLocation().getName() != null) {
      return getPermanentLocation().getName();
    }
    return "";
  }

  public Item changeStatus(ItemStatusName newStatus) {
    final var newChanged = isNotInStatus(newStatus);

    final var destinationServicePoint = newStatus == IN_TRANSIT
      ? this.inTransitDestinationServicePoint
      : null;

    // The previous code did not change the item status date
    // that behaviour has been preserved even though it is confusing
    return new Item(this.id, this.location, this.lastCheckIn,
      this.callNumberComponents, this.permanentLocation,
      destinationServicePoint, newChanged, this.holdings,
      this.instance, this.materialType, this.loanType, this.description,
      new ItemStatus(newStatus, status.getDate()));
  }

  Item available() {
    return changeStatus(AVAILABLE)
      .removeDestination();
  }

  Item inTransitToHome() {
    return changeStatus(IN_TRANSIT)
      .withInTransitDestinationServicePoint(getPrimaryServicePoint());
  }

  Item inTransitToServicePoint(UUID destinationServicePointId) {
    return changeStatus(IN_TRANSIT)
      .changeDestination(destinationServicePointId);
  }

  public Item updateDestinationServicePoint(ServicePoint servicePoint) {
    return withInTransitDestinationServicePoint(servicePoint);
  }

  public Item updateLastCheckInServicePoint(ServicePoint servicePoint) {
    if (lastCheckIn != null) {
      lastCheckIn.setServicePoint(servicePoint);
    }
    return this;
  }

  private Item changeDestination(@NonNull UUID destinationServicePointId) {
    return withInTransitDestinationServicePoint(
      ServicePoint.unknown(destinationServicePointId.toString()));
  }

  private Item removeDestination() {
    return withInTransitDestinationServicePoint(null);
  }

  public boolean isNotFound() {
    return !isFound();
  }

  public boolean isFound() {
    // this relies on items that are completely unknown are defined without an ID
    // this might not represent the case where an item's ID is known yet that
    // record could not be found / fetched
    return id != null;
  }

  public LastCheckIn getLastCheckIn() {
    return lastCheckIn;
  }

  public String getPermanentLocationId() {
    return firstNonBlank(permanentLocation.getId(), holdings.getPermanentLocationId());
  }

  public Item withLocation(@NonNull Location newLocation) {
    return new Item(this.id, newLocation, this.lastCheckIn,
      this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      this.instance, this.materialType, this.loanType, this.description,
      this.status);
  }

  public Item withMaterialType(@NonNull MaterialType materialType) {
    return new Item(this.id, this.location, this.lastCheckIn,
      this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      this.instance, materialType, this.loanType, this.description,
      this.status);
  }

  public Item withHoldings(@NonNull Holdings holdings) {
    return new Item(this.id, this.location, this.lastCheckIn,
      this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, holdings,
      this.instance, this.materialType, this.loanType, this.description,
      this.status);
  }

  public Item withInstance(@NonNull Instance instance) {
    return new Item(this.id, this.location, this.lastCheckIn,
      this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      instance, this.materialType, this.loanType, this.description,
      this.status);
  }

  public Item withLoanType(@NonNull LoanType loanType) {
    return new Item(this.id, this.location, this.lastCheckIn,
      this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      this.instance, this.materialType, loanType, this.description,
      this.status);
  }

  public Item withLastCheckIn(@NonNull LastCheckIn lastCheckIn) {
    return new Item(this.id, this.location, lastCheckIn,
      this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      this.instance, this.materialType, this.loanType, this.description,
      this.status);
  }

  public Item withPermanentLocation(@NonNull Location permanentLocation) {
    return new Item(this.id, this.location, this.lastCheckIn,
      this.callNumberComponents, permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      this.instance, this.materialType, this.loanType, this.description,
      this.status);
  }

  public Item withInTransitDestinationServicePoint(ServicePoint servicePoint) {
    return new Item(this.id, this.location, this.lastCheckIn,
      this.callNumberComponents, this.permanentLocation,
      servicePoint, this.changed, this.holdings, this.instance,
      this.materialType, this.loanType, this.description, this.status);
  }
}
