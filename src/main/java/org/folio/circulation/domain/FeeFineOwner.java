package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class FeeFineOwner {
  private final JsonObject representation;

  public FeeFineOwner() {
    representation = null;
  }

  public FeeFineOwner(JsonObject representation) {
    this.representation = representation;
  }

  public static FeeFineOwner from(JsonObject representation) {
    return new FeeFineOwner(representation);
  }

  public String getId() {
    return this.representation.getString("id");
  }

  public String getOwner() {
    return this.representation.getString("owner");
  }

  public boolean isInitialized() {
    return representation != null;
  }
}
