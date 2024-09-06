package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.Library;

import io.vertx.core.json.JsonObject;

public class LibraryMapper {
  public Library toDomain(JsonObject representation) {
    return new Library(getProperty(representation, "id"),
      getProperty(representation, "name"),
      getProperty(representation, "code"));
  }
}
