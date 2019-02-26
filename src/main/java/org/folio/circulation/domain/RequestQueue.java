package org.folio.circulation.domain;

import static java.util.Comparator.naturalOrder;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;

import java.util.Collection;
import java.util.List;
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

  public boolean hasAwaitingPickupRequestForOtherPatron(
    User requestingUser) {

    if(!hasOutstandingFulfillableRequests()) {
      return false;
    }
    else {
      final Request highestPriority = getHighestPriorityFulfillableRequest();

      return highestPriority.isAwaitingPickup()
        && !highestPriority.isFor(requestingUser);
    }
  }

  private List<Request> fulfillableRequests() {
    return requests
      .stream()
      .filter(Request::isFulfillable)
      .collect(Collectors.toList());
  }

  Integer nextAvailablePosition() {
    return highestPosition() + 1;
  }

  private Integer highestPosition() {
    return requests.stream()
      .filter(Request::isOpen)
      .map(request -> request.asJson().getInteger("position"))
      .max(naturalOrder()).orElse(0);
  }

  public void remove(Request request) {
    requests = removeInCollection(request);
    request.removePosition();
    removeGapsInPositions();
  }

  private List<Request> removeInCollection(Request request) {
    return requests.stream()
      .filter(r -> !r.getId().equals(request.getId()))
      .collect(Collectors.toList());
  }

  private void removeGapsInPositions() {
    Integer currentPosition = 1;

    for (Request request : requests) {
      request.changePosition(currentPosition);
      currentPosition++;
    }
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
      .collect(Collectors.toList());
  }

  //TODO: Encapsulate this better
  public Collection<Request> getRequests() {
    return requests;
  }
}
