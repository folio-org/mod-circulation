package org.folio.circulation.storage.mappers;

import org.folio.circulation.domain.ServicePoint;

import io.vertx.core.json.JsonObject;

public class ServicePointMapper {
  public ServicePoint toDomain(JsonObject representation) {
    return new ServicePoint(representation);
  }
}
