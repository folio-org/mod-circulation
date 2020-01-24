package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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

  public List<String> getServicePoints() {
    return representation.getJsonArray("servicePointOwner").stream()
      .map(e -> ((JsonObject) e).getString("value"))
    .collect(Collectors.toList());
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
