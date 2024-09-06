package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.Campus;

import io.vertx.core.json.JsonObject;

public class CampusMapper {
  public Campus toDomain(JsonObject representation) {
    return new Campus(getProperty(representation, "id"),
      getProperty(representation, "name"),
      getProperty(representation, "code"));
  }
}
