package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.IdentifierType;

import io.vertx.core.json.JsonObject;

public class IdentifierTypeMapper {
  public IdentifierType toDomain(JsonObject representation) {
    return new IdentifierType(
      getProperty(representation,"id"),
      getProperty(representation, "name"),
      getProperty(representation, "source"));
  }
}
