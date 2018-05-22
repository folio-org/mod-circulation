package org.folio.circulation.domain;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

public class RequestQueue {
  private final List<Request> requests;

  RequestQueue(List<Request> requests) {
    this.requests = requests;
  }

  boolean hasOutstandingRequests() {
    return !openRequests().isEmpty();
  }

  Request getHighestPriorityRequest() {
    return openRequests().get(0);
  }

  boolean hasOutstandingFulfillableRequests() {
    return !fulfillableRequests().isEmpty();
  }

  Request getHighestPriorityFulfillableRequest() {
    return fulfillableRequests().get(0);
  }

  private List<Request> fulfillableRequests() {
    return requests
      .stream()
      .filter(this::isFulfillable)
      .collect(Collectors.toList());
  }

  private boolean isFulfillable(Request request) {
    return StringUtils.equals(request.getString("fulfilmentPreference"),
      RequestFulfilmentPreference.HOLD_SHELF);
  }

  private boolean isOpen(Request request) {
    String status = request.getString("status");

    return StringUtils.equals(status, RequestStatus.OPEN_AWAITING_PICKUP)
      || StringUtils.equals(status, RequestStatus.OPEN_NOT_YET_FILLED);
  }

  private List<Request> openRequests() {
    return requests.stream()
      .filter(this::isOpen)
      .collect(Collectors.toList());
  }
}
