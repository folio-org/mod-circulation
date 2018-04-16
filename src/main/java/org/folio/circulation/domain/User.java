package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class User extends JsonObject {
  public User(JsonObject representation) {
    super(representation.getMap());
  }
}
