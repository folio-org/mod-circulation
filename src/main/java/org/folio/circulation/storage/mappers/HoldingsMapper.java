package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Holdings;

import io.vertx.core.json.JsonObject;

public class HoldingsMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public Holdings toDomain(JsonObject holdingsRepresentation) {
    log.debug("toDomain:: parameters holdingsId: {}", () -> getProperty(holdingsRepresentation, "id"));
    return new Holdings(getProperty(holdingsRepresentation, "id"),
      getProperty(holdingsRepresentation, "instanceId"),
      getProperty(holdingsRepresentation, "copyNumber"),
      getProperty(holdingsRepresentation, "permanentLocationId"),
      getProperty(holdingsRepresentation, "effectiveLocationId"));
  }
}
