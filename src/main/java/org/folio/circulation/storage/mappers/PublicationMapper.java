package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.Publication;

import io.vertx.core.json.JsonObject;

public class PublicationMapper {
  public Publication toDomain(JsonObject representation) {

    return new Publication(getProperty(representation, "publisher"),
      getProperty(representation, "place"),
      getProperty(representation, "dateOfPublication"),
      getProperty(representation, "role"));
  }
}
