package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.ServicePoint;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.ExpirationDateManagement;

public class ServicePointMapper {
  public ServicePoint toDomain(JsonObject representation) {
    final var timePeriodMapper = new TimePeriodMapper();

    return new ServicePoint(getProperty(representation, "id"),
      getProperty(representation, "name"),
      getProperty(representation, "code"),
      getBooleanProperty(representation, "pickupLocation"),
      getProperty(representation, "discoveryDisplayName"),
      getProperty(representation, "description"),
      getIntegerProperty(representation, "shelvingLagTime",
        null),
      timePeriodMapper.toDomain(
        getObjectProperty(representation, "holdShelfExpiryPeriod")),
      ExpirationDateManagement.getExpirationDateManagement(
        getProperty(representation, "holdShelfClosedLibraryDateManagement")));
  }
}
