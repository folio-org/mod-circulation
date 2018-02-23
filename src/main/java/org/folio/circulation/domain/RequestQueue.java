package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import java.util.List;

public class RequestQueue {
  private final List<JsonObject> requests;

  RequestQueue(List<JsonObject> requests) {
    this.requests = requests;
  }

  public boolean hasOutstandingRequests() {
    return !requests.isEmpty();
  }

  public JsonObject getHighestPriority() {
    return requests.get(0);
  }
}
