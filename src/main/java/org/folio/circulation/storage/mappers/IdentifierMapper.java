package org.folio.circulation.storage.mappers;

import java.util.List;

import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.Identifier;

import io.vertx.core.json.JsonObject;

public class IdentifierMapper {
  public Identifier toDomain(JsonObject representation) {
    return new Identifier(getProperty(representation,"identifierTypeId"),
      getProperty(representation, "value"));
  }

  public static List<Identifier> mapIdentifiers(JsonObject representation) {
    return mapToList(representation, "identifiers", new IdentifierMapper()::toDomain);
  }
}
