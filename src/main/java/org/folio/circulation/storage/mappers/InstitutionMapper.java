package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Institution;

import io.vertx.core.json.JsonObject;

public class InstitutionMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public Institution toDomain(JsonObject representation) {
    log.debug("toDomain:: parameters institutionId: {}", () -> getProperty(representation, "id"));
    return new Institution(getProperty(representation, "id"),
      getProperty(representation, "name"),
      getProperty(representation, "code"));
  }
}
