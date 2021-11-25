package org.folio.circulation.storage.mappers;

import org.folio.circulation.domain.MaterialType;

import io.vertx.core.json.JsonObject;

public class MaterialTypeMapper {
  public MaterialType toDomain(JsonObject representation) {
    return new MaterialType();
  }
}
