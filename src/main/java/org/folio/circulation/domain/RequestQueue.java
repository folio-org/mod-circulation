package org.folio.circulation.domain;

import static java.util.Arrays.asList;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

public class RequestQueue {
  private List<Request> requests;
  private final List<UpdatedRequestPair> updatedRequests;

  public static RequestQueue requestQueueOf(Request... requests) {
    return new RequestQueue(asList(requests));
  }

  public RequestQueue(Collection<Request> requests) {
    this.requests = new ArrayList<>(requests);
    updatedRequests = new ArrayList<>();

    // Ordering requests by position, so we can add and remove them
    // without sorting and just re-sequence from 1 to n
    this.requests.sort(Comparator
      .comparingInt(request -> Optional.ofNullable(request.getPosition())
        .orElse(0)
      ));
  }

  public RequestQueue filter(Predicate<Request> predicate) {
    return new RequestQueue(requests.stream()
      .filter(predicate)
      .collect(Collectors.toList()));
  }

  ItemStatus checkedInItemStatus(Item item) {
    return hasOutstandingFulfillableRequests()
      ? getHighestPriorityRequestFulfillableByItem(item).checkedInItemStatus()
      : AVAILABLE;
  }

  boolean hasOutstandingFulfillableRequests() {
    return !fulfillableRequests().isEmpty();
  }

  Request getHighestPriorityFulfillableRequest() {
    return fulfillableRequests().get(0);
  }

  Request getHighestPriorityRequestFulfillableByItem(Item item) {
    return fulfillableRequests().stream()
      .filter(request -> requestIsFulfillableByItem(request, item))
      .findFirst()
      .orElse(null);
  }

  private boolean requestIsFulfillableByItem(Request request, Item item) {
    if (request.getRequestLevel() == RequestLevel.TITLE) {
      String itemInstanceId = item.getInstanceId();
      String requestInstanceId = request.getInstanceId();
      String requestItemId = request.getItemId();
      String itemId = item.getItemId();

      return itemInstanceId != null && itemInstanceId.equals(requestInstanceId)
        && (requestItemId == null ^ (item.isFound() && itemId.equals(requestItemId)));
    }
    else if (request.getRequestLevel() == RequestLevel.ITEM) {
      String itemId = item.getItemId();
      String requestItemId = request.getItemId();

      return itemId != null && itemId.equals(requestItemId);
    }

    return false;
  }

  boolean containsRequestOfType(RequestType type) {
    return requests.stream().anyMatch(request -> request.getRequestType() == type);
  }

  public boolean hasOpenRecalls() {
    return requests.stream()
        .anyMatch(request -> request.getRequestType() == RequestType.RECALL && request.isNotYetFilled());
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
    reSequenceRequests();
  }

  public void update(Request original, Request updated) {
    updatedRequests.add(new UpdatedRequestPair(original, updated));
  }

  public void remove(Request request) {
    requests = requests.stream()
      .filter(r -> !r.getId().equals(request.getId()))
      .collect(Collectors.toList());
    request.removePosition();
    reSequenceRequests();
  }

  private void reSequenceRequests() {
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

  public Collection<Request> getRequestsWithChangedPosition() {
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

  public List<UpdatedRequestPair> getUpdatedRequests() {
    return updatedRequests;
  }

  boolean isEmpty() {
    return getRequests().isEmpty();
  }

  // puts request on top of all requests in status "Open - Not yet filled"
  public void updateRequestPositionOnCheckIn(String requestId) {
    int newIndex = -1;

    for (int i = 0; i < requests.size(); i++) {
      if (newIndex == -1) {
        if (requests.get(i).isNotYetFilled()) {
          newIndex = i;
        }
      } else if (StringUtils.equals(requestId, requests.get(i).getId())) {
        var requestToMove = requests.remove(i);
        requests.add(newIndex, requestToMove);
        reSequenceRequests();
        return;
      }
    }
  }

}
