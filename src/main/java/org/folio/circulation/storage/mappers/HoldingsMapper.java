package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.Holdings;

import io.vertx.core.json.JsonObject;

public class HoldingsMapper {
  public Holdings toDomain(JsonObject holdingsRepresentation) {
    return new Holdings(getProperty(holdingsRepresentation, "id"),
      getProperty(holdingsRepresentation, "instanceId"),
      getProperty(holdingsRepresentation, "copyNumber"),
      getProperty(holdingsRepresentation, "permanentLocationId"),
      getProperty(holdingsRepresentation, "effectiveLocationId"));
  }
}
