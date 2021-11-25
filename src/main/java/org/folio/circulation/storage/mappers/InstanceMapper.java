package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.Instance;

import io.vertx.core.json.JsonObject;

public class InstanceMapper {
  public Instance toDomain(JsonObject representation) {
    return new Instance(getProperty(representation, "title"));
  }
}
