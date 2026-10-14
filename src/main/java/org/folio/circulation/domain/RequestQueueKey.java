package org.folio.circulation.domain;

import java.util.Objects;

import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.resources.context.ReorderRequestContext;

/** Identifies the effective queue whose positions are being changed. */
public record RequestQueueKey(QueueType queueType, String queueId) {
  public RequestQueueKey {
    Objects.requireNonNull(queueType, "queueType is required");
    Objects.requireNonNull(queueId, "queueId is required");
  }

  public static RequestQueueKey from(RequestAndRelatedRecords records) {
    Request request = records.getRequest();
    return from(records.isTlrFeatureEnabled(), request.getInstanceId(), request.getItemId());
  }

  public static RequestQueueKey from(LoanAndRelatedRecords records) {
    Item item = records.getItem();
    return from(records.getTlrSettings(), item.getInstanceId(), item.getItemId());
  }

  public static RequestQueueKey from(Request request,
    TlrSettingsConfiguration tlrSettings) {

    return from(tlrSettings, request.getInstanceId(), request.getItemId());
  }

  public static RequestQueueKey from(Item item,
    TlrSettingsConfiguration tlrSettings) {

    return from(tlrSettings, item.getInstanceId(), item.getItemId());
  }

  public static RequestQueueKey from(ReorderRequestContext context) {
    return context.isQueueForInstance()
      ? forInstance(context.getIdParamValue())
      : forItem(context.getIdParamValue());
  }

  public static RequestQueueKey from(TlrSettingsConfiguration tlrSettings,
    String instanceId, String itemId) {

    boolean tlrEnabled = tlrSettings != null
      && tlrSettings.isTitleLevelRequestsFeatureEnabled();
    return from(tlrEnabled, instanceId, itemId);
  }

  public static RequestQueueKey from(boolean tlrEnabled, String instanceId, String itemId) {
    return tlrEnabled ? forInstance(instanceId) : forItem(itemId);
  }

  public static RequestQueueKey forInstance(String instanceId) {
    return new RequestQueueKey(QueueType.INSTANCE, instanceId);
  }

  public static RequestQueueKey forItem(String itemId) {
    return new RequestQueueKey(QueueType.ITEM, itemId);
  }

  public enum QueueType {
    INSTANCE,
    ITEM
  }
}
