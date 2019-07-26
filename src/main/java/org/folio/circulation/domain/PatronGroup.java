package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class PatronGroup {
  private final JsonObject representation;
  
  public PatronGroup(JsonObject newRepresentation) {
    this.representation = newRepresentation;
  }
  
  public static PatronGroup from(JsonObject newRepresentation) {
    return new PatronGroup(newRepresentation);
  }

  public static PatronGroup unknown(String id) {
    return new UnknownPatronGroup(id);
  }
  
  public String getId() {
    return getProperty(representation, "id");
  }
  
  public String getGroup() {
    return getProperty(representation, "group");
  }
  
  public String getDesc() {
    return getProperty(representation, "desc");
  }

  private static class UnknownPatronGroup extends PatronGroup {
    UnknownPatronGroup(String id) {
      super(new JsonObject().put("id", id));
    }
  }
}
