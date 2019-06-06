package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class Library {

  private final JsonObject representation;

  public Library(JsonObject newRepresentation) {
    this.representation = newRepresentation;
  }

  public static Library from(JsonObject newRepresentation) {
    return new Library(newRepresentation);
  }

  public String getId() {
    return getProperty(representation, "id");
  }

  public String getName() {
    return getProperty(representation, "name");
  }
}
