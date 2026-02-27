package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Campus;

import io.vertx.core.json.JsonObject;

public class CampusMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public Campus toDomain(JsonObject representation) {
    log.debug("toDomain:: parameters campusId: {}", () -> getProperty(representation, "id"));
    return new Campus(getProperty(representation, "id"),
      getProperty(representation, "name"),
      getProperty(representation, "code"));
  }
}
