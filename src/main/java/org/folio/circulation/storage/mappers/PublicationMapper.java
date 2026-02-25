package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Publication;

import io.vertx.core.json.JsonObject;

public class PublicationMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public Publication toDomain(JsonObject representation) {
    log.debug("toDomain:: parameters publisher: {}", () -> getProperty(representation, "publisher"));
    return new Publication(getProperty(representation, "publisher"),
      getProperty(representation, "place"),
      getProperty(representation, "dateOfPublication"),
      getProperty(representation, "role"));
  }
}
