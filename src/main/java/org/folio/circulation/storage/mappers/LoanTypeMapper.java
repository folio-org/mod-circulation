package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.LoanType;

import io.vertx.core.json.JsonObject;

public class LoanTypeMapper {
  public LoanType toDomain(JsonObject representation) {
    return new LoanType(getProperty(representation, "id"),
      getProperty(representation, "name"));
  }
}
