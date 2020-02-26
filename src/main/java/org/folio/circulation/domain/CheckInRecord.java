package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import org.apache.commons.lang3.ObjectUtils;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class CheckInRecord {
  private static final String OCCURRED_DATE_TIME = "occurredDateTime";
  private static final String ITEM_ID = "itemId";
  private static final String SERVICE_POINT_ID = "servicePointId";
  private static final String PERFORMED_BY_USER_ID = "performedByUserId";

  private final String id;
  private final DateTime occurredDateTime;
  private final String itemId;
  private final String servicePointId;
  private final String performedByUserId;
  private final String itemStatus;
  private final String itemLocationId;
  private final Integer requestQueueSize;

  private CheckInRecord(Builder builder) {
    this.id = builder.id;
    this.occurredDateTime = builder.occurredDateTime;
    this.itemId = builder.itemId;
    this.servicePointId = builder.checkInServicePointId;
    this.performedByUserId = builder.performedByUserId;
    this.itemStatus = builder.itemStatus;
    this.itemLocationId = builder.itemLocationId;
    this.requestQueueSize = builder.requestQueueSize;
  }

  public static class Builder {
    private String id;
    private DateTime occurredDateTime;
    private String itemId;
    private String checkInServicePointId;
    private String performedByUserId;
    private String itemStatus;
    private String itemLocationId;
    private Integer requestQueueSize;

    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    public Builder withOccurredDateTime(DateTime occurredDateTime) {
      this.occurredDateTime = occurredDateTime;
      return this;
    }

    public Builder withItemId(String itemId) {
      this.itemId = itemId;
      return this;
    }

    public Builder withServicePointId(String servicePointId) {
      this.checkInServicePointId = servicePointId;
      return this;
    }

    public Builder withPerformedByUserId(String performedByUserId) {
      this.performedByUserId = performedByUserId;
      return this;
    }

    public Builder withItemStatus(String itemStatus) {
      this.itemStatus = itemStatus;
      return this;
    }

    public Builder withItemLocationId(String itemLocationId) {
      this.itemLocationId = itemLocationId;
      return this;
    }

    public Builder withRequestQueueSize(Integer queueSize) {
      this.requestQueueSize = queueSize;
      return this;
    }

    public CheckInRecord build() {
      if (!ObjectUtils.allNotNull(occurredDateTime, itemId,
        checkInServicePointId, performedByUserId)) {

        throw new IllegalStateException(
          "occurredDateTime, itemId, checkInServicePoint and performedByUserId are required");
      }

      return new CheckInRecord(this);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject()
      .put(OCCURRED_DATE_TIME, occurredDateTime.toString())
      .put(ITEM_ID, itemId)
      .put(SERVICE_POINT_ID, servicePointId)
      .put(PERFORMED_BY_USER_ID, performedByUserId);

    write(json, "id", id);
    write(json, "itemStatus", itemStatus);
    write(json, "itemLocationId", itemLocationId);
    write(json, "requestQueueSize", requestQueueSize);

    return json;
  }
}
