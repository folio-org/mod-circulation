package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

public class RequestQueue {
  private final List<JsonObject> requests;

  RequestQueue(List<JsonObject> requests) {
    this.requests = requests;
  }

  public boolean hasOutstandingRequests() {
    return !requests.isEmpty();
  }

  public JsonObject getHighestPriorityRequest() {
    return requests.get(0);
  }

  public boolean hasOutstandingFulfillableRequests() {
    return !fulfillableRequests().isEmpty();
  }

  public JsonObject getHighestPriorityFulfillableRequest() {
    return fulfillableRequests().get(0);
  }

  private List<JsonObject> fulfillableRequests() {
    return requests
      .stream()
      .filter(request ->
        StringUtils.equals(request.getString("fulfilmentPreference"),
          RequestFulfilmentPreference.HOLD_SHELF))
      .collect(Collectors.toList());
  }
}
