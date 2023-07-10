package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class CheckOutLock {

  JsonObject representation;

  public CheckOutLock(JsonObject representation) {
    this.representation = representation;
  }

  public static CheckOutLock from(JsonObject representation) {
    return new CheckOutLock(representation);
  }

  public String getId() {
    return this.representation.getString("id");
  }

}
