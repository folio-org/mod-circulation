package org.folio.circulation.resources.context;

import static org.folio.circulation.resources.context.RequestQueueType.FOR_INSTANCE;

import java.util.HashMap;
import java.util.Map;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.reorder.ReorderRequest;
import org.folio.circulation.domain.reorder.ReorderQueueRequest;

import lombok.Getter;

@Getter
public class ReorderRequestContext {
  private final RequestQueueType requestQueueType;

  // Can be either instanceId or itemId depending on the queue type
  private final String idParamValue;

  private final ReorderQueueRequest reorderQueueRequest;
  private RequestQueue requestQueue;

  public ReorderRequestContext(RequestQueueType requestQueueType, String idParamValue,
    ReorderQueueRequest reorderQueueRequest) {

    this.requestQueueType = requestQueueType;
    this.idParamValue = idParamValue;
    this.reorderQueueRequest = reorderQueueRequest;
  }

  public ReorderRequestContext withRequestQueue(RequestQueue requestQueue) {
    this.requestQueue = requestQueue;

    return this;
  }

  public boolean isQueueForInstance() {
    return requestQueueType == FOR_INSTANCE;
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
