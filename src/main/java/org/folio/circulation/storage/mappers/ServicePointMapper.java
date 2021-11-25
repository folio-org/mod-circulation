package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.TimePeriod;

import io.vertx.core.json.JsonObject;

public class ServicePointMapper {
  public ServicePoint toDomain(JsonObject representation) {
    return new ServicePoint(getProperty(representation, "id"),
      getProperty(representation, "name"),
      getProperty(representation, "code"),
      getBooleanProperty(representation, "pickupLocation"),
      getProperty(representation, "discoveryDisplayName"),
      getProperty(representation, "description"),
      getIntegerProperty(representation, "shelvingLagTime",
        null),
      TimePeriod.from(getObjectProperty(representation, "holdShelfExpiryPeriod")));
  }
}
