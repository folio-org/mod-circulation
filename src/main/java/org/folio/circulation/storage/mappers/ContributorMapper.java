package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Contributor;

import io.vertx.core.json.JsonObject;

public class ContributorMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public Contributor toDomain(JsonObject representation) {
    log.debug("toDomain:: parameters contributorName: {}", () -> getProperty(representation, "name"));
    return new Contributor(getProperty(representation, "name"),
      getBooleanProperty(representation, "primary"));
  }
}
