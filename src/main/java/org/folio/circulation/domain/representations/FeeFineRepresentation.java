package org.folio.circulation.domain.representations;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class FeeFineRepresentation extends JsonObject {

  public FeeFineRepresentation(String ownerId, String feeFineType) {
    super();
    this.put("id", UUID.randomUUID().toString());
    this.put("ownerId", ownerId);
    this.put("feeFineType", feeFineType);
  }
}
