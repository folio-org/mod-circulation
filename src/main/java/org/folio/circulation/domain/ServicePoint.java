package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;

public class ServicePoint {
  private final String name;
  private final String id;
  private final String code;
  private final String discoveryDisplayName;
  private final String description;
  private final Integer shelvingLagTime;
  private final TimePeriod holdShelfExpiryPeriod;
  private final boolean pickupLocation;

  public ServicePoint(JsonObject representation) {
    name = getProperty(representation, "name");
    id = getProperty(representation, "id");
    code = getProperty(representation, "code");
    pickupLocation = getBooleanProperty(representation, "pickupLocation");
    discoveryDisplayName = getProperty(representation, "discoveryDisplayName");
    description = getProperty(representation, "description");
    shelvingLagTime = getIntegerProperty(representation, "shelvingLagTime",
      null);
    holdShelfExpiryPeriod = TimePeriod.from(
      getObjectProperty(representation, "holdShelfExpiryPeriod"));
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
