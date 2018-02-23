package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import java.util.List;

public class RequestQueue {
  private final List<JsonObject> requests;

  RequestQueue(List<JsonObject> requests) {
    this.requests = requests;
  }

  boolean hasOutstandingRequests() {
    return !requests.isEmpty();
  }

  public JsonObject getFirst() {
    return requests.get(0);
  }
}
