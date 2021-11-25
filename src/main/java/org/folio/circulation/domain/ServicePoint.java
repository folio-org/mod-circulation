package org.folio.circulation.domain;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ServicePoint {
  private final String id;
  private final String name;
  private final String code;
  private final boolean pickupLocation;
  private final String discoveryDisplayName;
  private final String description;
  private final Integer shelvingLagTime;
  private final TimePeriod holdShelfExpiryPeriod;

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
