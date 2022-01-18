package org.folio.circulation.domain;

import lombok.Value;

@Value
public class ServicePoint {
  public static ServicePoint unknown(String id) {
    return new ServicePoint(id, null, null, false, null, null, null, null);
  }

  String id;
  String name;
  String code;
  boolean pickupLocation;
  String discoveryDisplayName;
  String description;
  Integer shelvingLagTime;
  TimePeriod holdShelfExpiryPeriod;
}
