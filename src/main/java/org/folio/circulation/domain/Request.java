package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class Request {
  private final JsonObject representation;

  public Request(JsonObject representation) {
    this.representation = representation;
  }

  public String getString(String propertyName) {
    return representation.getString(propertyName);
  }

  public JsonObject asJson() {
    return representation.copy();
  }

  public void put(String propertyName, String value) {
    representation.put(propertyName, value);
  }
}
