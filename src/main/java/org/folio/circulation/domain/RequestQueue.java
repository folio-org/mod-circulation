package org.folio.circulation.domain;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.domain.RequestTypeItemStatusWhiteList.canCreateRequestForItem;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RequestQueue {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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
      .toList());
  }

  Request getHighestPriorityFulfillableRequest() {
    return fulfillableRequests().get(0);
  }

  boolean containsRequestOfTypeForItem(RequestType type, Item item) {
    return requests.stream().anyMatch(request -> request.getRequestType() == type && request.isFor(item));
  }

  public boolean hasOpenRecalls() {
    return requests.stream()
        .anyMatch(request -> request.getRequestType() == RequestType.RECALL && request.isNotYetFilled());
  }

  public List<String> getRecalledLoansIds() {
    return requests.stream()
      .filter(Request::isRecall)
      .map(Request::getLoan)
      .filter(Objects::nonNull)
      .map(Loan::getId)
      .toList();
  }

  public Loan getTheLeastRecalledLoan() {
    return requests.stream()
      .filter(Request::isRecall)
      .filter(Request::hasLoan)
      //Counting the amount of recalls for each loan
      .collect(collectingAndThen(groupingBy(Request::getLoan, counting()), m -> m.entrySet()
        .stream()
        .filter(entry -> canCreateRequestForItem(entry.getKey().getItemStatus(), RECALL))
        .min(Comparator.comparingLong(Map.Entry<Loan, Long>::getValue)
          .thenComparing(o -> o.getKey().getDueDate()))
        .map(Map.Entry::getKey)
        .orElse(null)));
  }

  public List<Request> fulfillableRequests() {
    return requests
      .stream()
      .filter(Request::isFulfillable)
      .toList();
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
    // MCBFF-211 diagnostics: queue order BEFORE removal (i.e. before a fulfilled/closed
    // request is taken out and the rest close ranks). Compare "before" vs "after" ids/order
    // below to see exactly how the remaining requests get renumbered - if a Hold and Recall
    // swap order here (as opposed to just shifting up), that would point to a bug in this
    // in-memory list rather than upstream ECS sync.
    logQueueSnapshot("remove:: BEFORE removing " + request.getId());

    requests = requests.stream()
      .filter(r -> !r.getId().equals(request.getId()))
      .collect(toList());
    request.removePosition();
    reSequenceRequests();

    logQueueSnapshot("remove:: AFTER removing " + request.getId());
  }

  private void logQueueSnapshot(String label) {
    log.info("MCBFF-211 {} -> [{}]", label, requests.stream()
      .map(r -> "id=" + r.getId() + ",type=" + r.getRequestType() + ",level="
        + r.getRequestLevel() + ",itemId=" + r.getItemId() + ",position=" + r.getPosition())
      .collect(java.util.stream.Collectors.joining(" | ")));
  }

  private void reSequenceRequests() {
    final AtomicInteger position = new AtomicInteger(1);
    requests.forEach(req -> req.changePosition(position.getAndIncrement()));

    // MCBFF-211 diagnostics: this is the confirmed type-blind/level-blind renumbering step -
    // it just walks the in-memory `requests` list in its CURRENT order and assigns 1..n. It
    // never looks at requestType/requestLevel. If the list order going INTO this method is
    // already wrong (e.g. Hold and Recall already swapped before this call), this method
    // will faithfully preserve that wrong order - it cannot itself be the source of a
    // Hold/Recall swap, only a passthrough. Logged here to confirm list order at the moment
    // positions are (re)assigned.
    logQueueSnapshot("reSequenceRequests:: AFTER renumbering (list order preserved, positions assigned 1..n)");
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
      .toList();
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
    // MCBFF-211 diagnostics: this method moves the request identified by requestId to sit
    // just above the first "Open - Not yet filled" request found scanning from the top -
    // i.e. it does NOT look at requestType/requestLevel, only status and current list order.
    // This is the check-in-time reordering (distinct from checkout's fulfillability
    // selection and distinct from the cross-tenant ECS sync). Logging list order before/after
    // to see if a Hold/Recall swap could originate here.
    logQueueSnapshot("updateRequestPositionOnCheckIn:: BEFORE, moving requestId=" + requestId);

    int newIndex = -1;

    for (int i = 0; i < requests.size(); i++) {
      var currentRequest = requests.get(i);
      boolean isSameRequest = StringUtils.equals(requestId, currentRequest.getId());

      if (newIndex == -1) {
        if (!isSameRequest && currentRequest.isNotYetFilled()) {
          newIndex = i;
          log.info("MCBFF-211 updateRequestPositionOnCheckIn:: target insertion index {} " +
              "found at request id={}, type={}, level={}, status={}", i,
            currentRequest.getId(), currentRequest.getRequestType(),
            currentRequest.getRequestLevel(), currentRequest.getStatus());
        }
      } else if (isSameRequest) {
        requests.add(newIndex, requests.remove(i));
        reSequenceRequests();
        logQueueSnapshot("updateRequestPositionOnCheckIn:: AFTER, moved requestId=" + requestId
          + " to index=" + newIndex);
        return;
      }
    }

    log.info("MCBFF-211 updateRequestPositionOnCheckIn:: no-op - requestId={} not found or no " +
      "eligible insertion point (queue unchanged)", requestId);
  }

  public void replaceRequest(Request newRequest) {
    if (newRequest.getId() == null) {
      log.warn("Failed attempt to replace request in the queue");
      return;
    }

    requests = requests.stream()
      .map(request -> {
        if (request.getId() != null && request.getId().equals(newRequest.getId())) {
          return newRequest;
        }
        return request;
      })
      .collect(toList());
  }

  @Override
  public String toString() {
    return format("RequestQueue(%s)",
      requests == null ? null : format("%d requests", requests.size()));
  }
}
