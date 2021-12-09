package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.Contributor;

import io.vertx.core.json.JsonObject;

public class ContributorMapper {
  public Contributor toDomain(JsonObject representation) {
    return new Contributor(getProperty(representation, "name"),
      getBooleanProperty(representation, "primary"));
  }
}
