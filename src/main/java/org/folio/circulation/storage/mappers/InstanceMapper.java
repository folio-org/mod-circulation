package org.folio.circulation.storage.mappers;

import org.folio.circulation.domain.Instance;

import io.vertx.core.json.JsonObject;

public class InstanceMapper {
  public Instance toDomain(JsonObject representation) {
    return new Instance();
  }
}
