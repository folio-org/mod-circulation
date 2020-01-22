package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class FeeFine {
  private final JsonObject representation;

  public FeeFine() {
    representation = null;
  }

  public FeeFine(JsonObject representation) {
    this.representation = representation;
  }

  public static FeeFine from(JsonObject representation) {
    return new FeeFine(representation);
  }

  public String getId() {
    return this.representation.getString("id");
  }

  public String getFeeFineType() {
    return this.representation.getString("feeFineType");
  }

  public boolean isInitialized() {
    return representation != null;
  }
}
