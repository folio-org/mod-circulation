package org.folio.circulation.resources.context;

import java.util.HashMap;
import java.util.Map;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.reorder.ReorderRequest;
import org.folio.circulation.domain.reorder.ReorderQueueRequest;

import lombok.Getter;

@Getter
public class ReorderRequestContext {
  private final String instanceId;
  private final String itemId;
  private final ReorderQueueRequest reorderQueueRequest;
  private RequestQueue requestQueue;

  public ReorderRequestContext(String instanceId, String itemId,
    ReorderQueueRequest reorderQueueRequest) {

    this.instanceId = instanceId;
    this.itemId = itemId;
    this.reorderQueueRequest = reorderQueueRequest;
  }

  public ReorderRequestContext withRequestQueue(RequestQueue requestQueue) {
    this.requestQueue = requestQueue;

    return this;
  }

  public boolean isQueueForInstance() {
    return instanceId != null && itemId == null;
  }

  /**
   * Returns Map of pairs where key is ReorderedRequest and value is Request from the queue.
   *
   * @return Map of pairs ReorderRequest - Request.
   */
  public Map<ReorderRequest, Request> getReorderRequestToRequestMap() {
    final Map<ReorderRequest, Request> reorderRequestToRequestMap = new HashMap<>();

    reorderQueueRequest.getReorderedQueue().forEach(reorderRequest -> {
      Request relatedRequest = requestQueue.getRequests().stream()
        .filter(request -> request.getId().equals(reorderRequest.getId()))
        .findAny().orElse(null);

      reorderRequestToRequestMap.put(reorderRequest, relatedRequest);
    });

    return reorderRequestToRequestMap;
  }
}
