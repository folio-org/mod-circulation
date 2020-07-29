package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getArrayProperty;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Note {
  private final JsonObject representation;

  public Note(JsonObject representation) {
    this.representation = representation;
  }

  public static Note from(JsonObject representation) {
    return new Note(representation);
  }

  String getId() {
    return getProperty(representation, "id");
  }

  String getTypeId() {
    return getProperty(representation, "typeId");
  }

  String getDomain() {
    return getProperty(representation, "domain");
  }

  String getTitleString() {
    return getProperty(representation, "title");
  }

  String getTitleContent() {
    return getProperty(representation, "content");
  }

  JsonArray getLinks() {
    return getArrayProperty(this.representation, "links");
  }
}
