package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.SeriesStatement;

import io.vertx.core.json.JsonObject;

public class SeriesMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public SeriesStatement toDomain(JsonObject representation) {
    log.debug("toDomain:: parameters authorityId: {}",
      () -> getProperty(representation, "authorityId"));
    return new SeriesStatement(
      getProperty(representation, "authorityId"),
      getProperty(representation, "value"));
  }
}
