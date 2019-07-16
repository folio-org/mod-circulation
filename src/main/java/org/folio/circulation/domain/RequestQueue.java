package org.folio.circulation.domain;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class RequestQueue {

  private Collection<Request> requests;

  RequestQueue(Collection<Request> requests) {
    this.requests = requests;
  }

  ItemStatus checkedInItemStatus() {
    return hasOutstandingFulfillableRequests()
      ? getHighestPriorityFulfillableRequest().checkedInItemStatus()
      : AVAILABLE;
  }

  boolean hasOutstandingFulfillableRequests() {
    return !fulfillableRequests().isEmpty();
  }

  Request getHighestPriorityFulfillableRequest() {
    return fulfillableRequests().get(0);
  }

  public boolean isRequestedByOtherPatron(User requestingUser) {
    if(!hasOutstandingFulfillableRequests()) {
      return false;
    }
    else {
      final Request highestPriority = getHighestPriorityFulfillableRequest();

      return !highestPriority.isFor(requestingUser);
    }
  }

  private List<Request> fulfillableRequests() {
    return requests
      .stream()
      .filter(Request::isFulfillable)
      .collect(Collectors.toList());
  }

  public void add(Request newRequest) {
    requests = new ArrayList<>(requests);
    requests.add(newRequest);
    orderRequests();
  }

  public void remove(Request request) {
    requests = requests.stream()
      .filter(r -> !r.getId().equals(request.getId()))
      .collect(Collectors.toList());
    request.removePosition();
    orderRequests();
  }

  private void orderRequests() {
    requests = requests.stream()
      // order by date descending
      .sorted((req1, req2) -> req1.getRequestDate().compareTo(req2.getRequestDate()))
      // order by (request status = "Open - In transit" or "Open - Awaiting pickup") first
      .sorted((req1, req2) -> Boolean.compare(req2.isNotDisplaceable(), req1.isNotDisplaceable()))
      .collect(Collectors.toList());
    final AtomicInteger position = new AtomicInteger(1);
    requests.forEach(req -> req.changePosition(position.getAndIncrement()));
  }

  public Integer size() {
    return requests.size();
  }

  public Boolean contains(Request request) {
      return requests.stream()
        .anyMatch(r -> r.getId().equals(request.getId()));
  }

  Collection<Request> getRequestsWithChangedPosition() {
    return requests.stream()
      .filter(Request::hasChangedPosition)
      // order by position descending
      .sorted((req1, req2) -> req2.getPosition().compareTo(req1.getPosition()))
      .collect(Collectors.toList());
  }

  //TODO: Encapsulate this better
  public Collection<Request> getRequests() {
    return requests;
  }
}
