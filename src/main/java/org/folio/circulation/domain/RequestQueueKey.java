package org.folio.circulation.domain;

import java.util.Objects;

/** Identifies the effective queue whose positions are being changed. */
public record RequestQueueKey(QueueType queueType, String queueId) {
  public RequestQueueKey {
    Objects.requireNonNull(queueType, "queueType is required");
    Objects.requireNonNull(queueId, "queueId is required");
  }

  public static RequestQueueKey from(RequestAndRelatedRecords records) {
    Request request = records.getRequest();
    return records.isTlrFeatureEnabled()
      ? new RequestQueueKey(QueueType.INSTANCE, request.getInstanceId())
      : new RequestQueueKey(QueueType.ITEM, request.getItemId());
  }

  public enum QueueType {
    INSTANCE,
    ITEM
  }
}
