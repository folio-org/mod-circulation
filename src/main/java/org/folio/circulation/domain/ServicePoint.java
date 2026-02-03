package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.ExpirationDateManagement;

import lombok.ToString;
import lombok.Value;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class ServicePoint {
  public static ServicePoint unknown() {
    return unknown(null);
  }
  public static ServicePoint unknown(String id) {
    return new ServicePoint(id, null, null, false, null, null, null, null, null);
  }

  public static ServicePoint unknown(String id, String name) {
    return new ServicePoint(id, name, null, false, null, null, null, null, null);
  }

  @ToString.Include
  String id;
  @ToString.Include
  String name;
  String code;
  boolean pickupLocation;
  String discoveryDisplayName;
  String description;
  Integer shelvingLagTime;
  TimePeriod holdShelfExpiryPeriod;
  ExpirationDateManagement holdShelfClosedLibraryDateManagement;
}
