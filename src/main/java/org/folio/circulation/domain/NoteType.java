package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;

public class NoteType {
  private final JsonObject representation;

  public NoteType(JsonObject representation) {
    this.representation = representation;
  }

  public static NoteType from(JsonObject representation) {
    return new NoteType(representation);
  }

  public String getId() {
    return getProperty(representation, "id");
  }

  public String getName() {
    return getProperty(representation, "typeName");
  }
}
