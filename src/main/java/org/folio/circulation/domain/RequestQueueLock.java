package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class RequestQueueLock {
  private final JsonObject representation;

  private RequestQueueLock(JsonObject representation) {
    this.representation = representation;
  }

  public static RequestQueueLock from(JsonObject representation) {
    return new RequestQueueLock(representation);
  }

  public String getId() {
    return representation.getString("id");
  }
}
