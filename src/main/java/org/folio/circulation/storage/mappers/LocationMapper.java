package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;


import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
      getProperty(representation, "code"),
      getProperty(representation, "discoveryDisplayName"),
      getServicePointIds(representation),
      getPrimaryServicePointId(representation),
      getBooleanProperty(representation, "isFloatingCollection"),
      Institution.unknown(getProperty(representation, "institutionId")),
      Campus.unknown(getProperty(representation, "campusId")),
      Library.unknown(getProperty(representation, "libraryId")),
      ServicePoint.unknown(getProperty(representation, "primaryServicePoint"),
        getProperty(representation, "effectiveLocationPrimaryServicePointName")));
  }

  private Collection<UUID> getServicePointIds(JsonObject representation) {
    return getArrayProperty(representation, "servicePointIds")
      .stream()
      .map(String.class::cast)
      .map(UUID::fromString)
      .collect(Collectors.toList());
  }

  private UUID getPrimaryServicePointId(JsonObject representation) {
    return Optional.ofNullable(getProperty(representation, "primaryServicePoint"))
      .map(UUID::fromString)
      .orElse(null);
  }
}
