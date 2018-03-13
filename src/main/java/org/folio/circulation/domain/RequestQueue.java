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
    return !openRequests().isEmpty();
  }

  public JsonObject getHighestPriorityRequest() {
    return openRequests().get(0);
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
      .filter(this::isFulfillable)
      .collect(Collectors.toList());
  }

  private boolean isFulfillable(JsonObject request) {
    return StringUtils.equals(request.getString("fulfilmentPreference"),
      RequestFulfilmentPreference.HOLD_SHELF);
  }

  private boolean isOpen(JsonObject request) {
    String status = request.getString("status");

    return StringUtils.equals(status, RequestStatus.OPEN_AWAITING_PICKUP)
      || StringUtils.equals(status, RequestStatus.OPEN_NOT_YET_FILLED);
  }

  private List<JsonObject> openRequests() {
    return requests.stream()
      .filter(this::isOpen)
      .collect(Collectors.toList());
  }
}
