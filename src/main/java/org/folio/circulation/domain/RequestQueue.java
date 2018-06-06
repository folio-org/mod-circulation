package org.folio.circulation.domain;

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
      .filter(Request::isFulfillable)
      .collect(Collectors.toList());
  }

  private List<Request> openRequests() {
    return requests.stream()
      .filter(Request::isOpen)
      .collect(Collectors.toList());
  }
}
