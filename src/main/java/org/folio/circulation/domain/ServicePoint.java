package org.folio.circulation.domain;

import lombok.Value;

@Value
public class ServicePoint {
  String id;
  String name;
  String code;
  boolean pickupLocation;
  String discoveryDisplayName;
  String description;
  Integer shelvingLagTime;
  TimePeriod holdShelfExpiryPeriod;
}
