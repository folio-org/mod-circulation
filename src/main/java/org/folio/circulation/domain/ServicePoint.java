package org.folio.circulation.domain;

public class ServicePoint {
  private final String name;
  private final String id;
  private final String code;
  private final String discoveryDisplayName;
  private final String description;
  private final Integer shelvingLagTime;
  private final TimePeriod holdShelfExpiryPeriod;
  private final boolean pickupLocation;

  public ServicePoint(String id, String name,
    String code, boolean pickupLocation, String discoveryDisplayName,
    String description, Integer shelvingLagTime,
    TimePeriod holdShelfExpiryPeriod) {

    this.name = name;
    this.id = id;
    this.code = code;
    this.pickupLocation = pickupLocation;
    this.discoveryDisplayName = discoveryDisplayName;
    this.description = description;
    this.shelvingLagTime = shelvingLagTime;
    this.holdShelfExpiryPeriod = holdShelfExpiryPeriod;
  }

  public boolean isPickupLocation() {
    return pickupLocation;
  }

  public String getName() {
    return name;
  }

  public String getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getDiscoveryDisplayName() {
    return discoveryDisplayName;
  }

  public String getDescription() {
    return description;
  }

  public Integer getShelvingLagTime() {
    return shelvingLagTime;
  }

  public TimePeriod getHoldShelfExpiryPeriod() {
    return holdShelfExpiryPeriod;
  }
}
