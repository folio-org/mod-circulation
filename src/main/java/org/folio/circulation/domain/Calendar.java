package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class Calendar {

  private final JsonObject representation;

  public Calendar(JsonObject representation) {
    this.representation = representation;
  }

  public JsonObject getRepresentation() {
    return representation;
  }
}
