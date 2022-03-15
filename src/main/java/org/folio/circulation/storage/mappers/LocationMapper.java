package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.Campus;
import org.folio.circulation.domain.Institution;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.ServicePoint;

import io.vertx.core.json.JsonObject;

public class LocationMapper {
  public Location toDomain(JsonObject representation) {
    return new Location(representation, null,
      Institution.unknown(getProperty(representation, "institutionId")),
      Campus.unknown(getProperty(representation, "campusId")),
      ServicePoint.unknown(getProperty(representation, "primaryServicePoint")));
  }
}
