package org.folio.circulation.storage.mappers;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.SeriesStatement;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

public class SeriesMapper {
  public SeriesStatement toDomain(JsonObject representation) {

    return new SeriesStatement(
      getProperty(representation, "authorityId"),
      getProperty(representation, "value"));
  }
}
