package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.Institution;

import io.vertx.core.json.JsonObject;

public class InstitutionMapper {
  public Institution toDomain(JsonObject representation) {
    return new Institution(getProperty(representation, "id"),
      getProperty(representation, "name"),
      getProperty(representation, "code"));
  }
}
