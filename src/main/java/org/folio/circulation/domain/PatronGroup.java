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
  
  public String getId() {
    return getProperty(representation, "id");
  }
  
  public String getGroup() {
    return getProperty(representation, "group");
  }
  
  public String getDesc() {
    return getProperty(representation, "desc");
  }
}
