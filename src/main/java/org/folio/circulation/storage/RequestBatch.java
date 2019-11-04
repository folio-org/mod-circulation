package org.folio.circulation.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.StoredRequestRepresentation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RequestBatch {
  private final List<Request> requests;

  public RequestBatch(Collection<Request> request) {
    this.requests = new ArrayList<>(request);
  }

  public List<Request> getRequests() {
    return requests;
  }

  public JsonObject toJson() {
    StoredRequestRepresentation storedRequestRepresentation
      = new StoredRequestRepresentation();

    List<JsonObject> requestsAsJson = getRequests().stream()
      .map(storedRequestRepresentation::storedRequest)
      .collect(Collectors.toList());

    JsonObject requestBatchAsJson = new JsonObject();
    requestBatchAsJson.put("requests", new JsonArray(requestsAsJson));

    return requestBatchAsJson;
  }
}
