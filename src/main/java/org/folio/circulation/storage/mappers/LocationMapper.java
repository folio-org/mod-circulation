package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.Campus;
import org.folio.circulation.domain.Institution;
import org.folio.circulation.domain.Library;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.ServicePoint;

import io.vertx.core.json.JsonObject;

public class LocationMapper {
  public Location toDomain(JsonObject representation) {
    return new Location(getProperty(representation, "id"),
      getProperty(representation, "name"),
      getProperty(representation, "code"), representation,
      Institution.unknown(getProperty(representation, "institutionId")),
      Campus.unknown(getProperty(representation, "campusId")),
      Library.unknown(getProperty(representation, "libraryId")),
      ServicePoint.unknown(getProperty(representation, "primaryServicePoint")));
  }
}
