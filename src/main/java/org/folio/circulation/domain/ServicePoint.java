package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;

public class ServicePoint {
  private final JsonObject representation;

  public ServicePoint(JsonObject representation) {
    this.representation = representation;
  }

  public boolean isPickupLocation() {
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
}
