package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class ServicePoint {
  private final JsonObject representation;
  private DateTimeZone tenantTimeZone;

  public ServicePoint(JsonObject representation) {
    this(representation, null);
  }

  public ServicePoint(JsonObject representation, DateTimeZone tenantTimeZone) {
    this.representation = representation;
    this.tenantTimeZone = tenantTimeZone;
  }

  public Boolean isPickupLocation() {
    return representation.getBoolean("pickupLocation");
  }

  public String getName() {
    return getProperty(representation, "name");
  }

  public String getId() {
    return getProperty(representation, "id");
  }

  public String getCode() {
    return getProperty(representation, "code");
  }

  public String getDiscoveryDisplayName() {
    return getProperty(representation, "discoveryDisplayName");
  }

  public String getDescription() {
    return getProperty(representation, "description");
  }

  public Integer getShelvingLagTime() {
    return getIntegerProperty(representation, "shelvingLagTime", null);
  }

  public Boolean getPickupLocation() {
    return getBooleanProperty(representation, "pickupLocation");
  }

  public TimePeriod getHoldShelfExpiryPeriod() {
    return TimePeriod.from(getObjectProperty(representation, "holdShelfExpiryPeriod"));
  }

  public static ServicePoint from(JsonObject representation) {
    return new ServicePoint(representation);
  }

  DateTimeZone getTenantTimeZone() {
    return tenantTimeZone;
  }

  ServicePoint withTenantTimeZone(DateTimeZone tenantTimeZone) {
    return new ServicePoint(representation, tenantTimeZone);
  }
}
