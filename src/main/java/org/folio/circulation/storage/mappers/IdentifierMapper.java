package org.folio.circulation.storage.mappers;

import java.lang.invoke.MethodHandles;
import java.util.List;

import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Identifier;

import io.vertx.core.json.JsonObject;

public class IdentifierMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public Identifier toDomain(JsonObject representation) {
    log.debug("toDomain:: parameters identifierTypeId: {}",
      () -> getProperty(representation, "identifierTypeId"));
    return new Identifier(getProperty(representation,"identifierTypeId"),
      getProperty(representation, "value"));
  }

  public static List<Identifier> mapIdentifiers(JsonObject representation) {
    return mapToList(representation, "identifiers", new IdentifierMapper()::toDomain);
  }
}
