package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.LoanType;

import io.vertx.core.json.JsonObject;

public class LoanTypeMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public LoanType toDomain(JsonObject representation) {
    log.debug("toDomain:: parameters loanTypeId: {}", () -> getProperty(representation, "id"));
    return new LoanType(getProperty(representation, "id"),
      getProperty(representation, "name"));
  }
}
